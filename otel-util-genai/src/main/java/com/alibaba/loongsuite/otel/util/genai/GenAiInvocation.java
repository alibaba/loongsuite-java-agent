/*
 * Copyright 2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.loongsuite.otel.util.genai;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

/**
 * Abstract base class managing the lifecycle of a GenAI telemetry span.
 *
 * <p>Subclasses represent specific GenAI operations (inference, embedding, tool execution, agent
 * invocation, workflow invocation) and provide operation-specific attributes and metrics.
 *
 * <p>Implements {@link AutoCloseable} for use with try-with-resources. If neither {@link #stop()}
 * nor {@link #fail(Throwable)} has been called when {@link #close()} is invoked, the invocation is
 * completed as a success via {@code stop()}. Callers should explicitly call {@link
 * #fail(Throwable)} in a catch block to record errors.
 */
public abstract class GenAiInvocation implements AutoCloseable {

  private static final Logger logger = Logger.getLogger(GenAiInvocation.class.getName());

  protected final GenAiTelemetryHandler handler;
  protected final Span span;
  protected final Scope scope;
  protected final long startTimeNanos;
  protected final Map<String, String> extraAttributes = new LinkedHashMap<>();
  protected final Map<String, String> metricAttributes = new LinkedHashMap<>();
  protected @Nullable String errorType;
  private double duration;
  private boolean finished = false;

  /** Package-private constructor. Invocations are created by {@link GenAiTelemetryHandler}. */
  GenAiInvocation(GenAiTelemetryHandler handler, Span span, Scope scope) {
    this.handler = handler;
    this.span = span;
    this.scope = scope;
    this.startTimeNanos = System.nanoTime();
  }

  // ---------------------------------------------------------------------------
  // Public lifecycle
  // ---------------------------------------------------------------------------

  /** Completes this invocation as a success, recording all attributes and metrics. */
  public void stop() {
    finish(null, null, null);
  }

  /**
   * Completes this invocation as a failure, setting the span status to {@link StatusCode#ERROR} and
   * recording the error type derived from the throwable's simple class name (matching Python's
   * {@code type(exception).__qualname__}).
   */
  public void fail(Throwable error) {
    finish(error.getClass().getSimpleName(), error.getMessage(), error);
  }

  /**
   * Completes this invocation as a failure with explicit error type and message strings.
   *
   * @param errorType the error type value for the {@code error.type} attribute
   * @param errorMessage the error message for the span status description
   */
  public void fail(String errorType, String errorMessage) {
    finish(errorType, errorMessage, null);
  }

  /**
   * Completes the invocation if not already finished. Does not throw checked exceptions.
   *
   * <p><b>Important:</b> Java's try-with-resources does not pass the in-flight exception to {@code
   * close()}, unlike Python's context manager {@code __exit__}. If an exception is thrown inside
   * the try block without calling {@link #fail(Throwable)} first, {@code close()} will complete the
   * invocation as a <em>success</em>. To ensure correct error recording, use one of:
   *
   * <pre>{@code
   * // Option 1: catch and fail explicitly (recommended)
   * try (var inv = handler.inference("openai", "gpt-4o")) {
   *     // ...
   * } catch (Exception e) {
   *     inv.fail(e);
   *     throw e;
   * }
   *
   * // Option 2: use the wrap() helper
   * handler.inferenceRun("openai", "gpt-4o", inv -> {
   *     // ... exceptions auto-captured ...
   * });
   * }</pre>
   */
  @Override
  public void close() {
    if (!finished) {
      stop();
    }
  }

  // ---------------------------------------------------------------------------
  // Public attribute setters
  // ---------------------------------------------------------------------------

  /** Adds a custom string attribute to the span. Applied when the invocation finishes. */
  public void setAttribute(String key, String value) {
    extraAttributes.put(key, value);
  }

  /** Sets a long attribute directly on the underlying span. */
  public void setAttribute(String key, long value) {
    span.setAttribute(key, value);
  }

  /** Sets a typed attribute directly on the underlying span. */
  public <T> void setAttribute(AttributeKey<T> key, T value) {
    span.setAttribute(key, value);
  }

  /** Adds a custom metric attribute included in duration and token histograms. */
  public void setMetricAttribute(String key, String value) {
    metricAttributes.put(key, value);
  }

  /** Returns the underlying {@link Span} for advanced use cases. */
  public Span getSpan() {
    return span;
  }

  // ---------------------------------------------------------------------------
  // Package-private accessors (used by GenAiTelemetryHandler)
  // ---------------------------------------------------------------------------

  /** Returns the computed operation duration in seconds. Valid only after {@link #stop()}. */
  double getDuration() {
    return duration;
  }

  /**
   * Merges the subclass metric attributes with any extra metric attributes set via {@link
   * #setMetricAttribute(String, String)}.
   */
  Map<String, String> getExtraAttributes() {
    return extraAttributes;
  }

  Attributes getAllMetricAttributes() {
    Attributes baseAttrs = buildMetricAttributes();
    if (metricAttributes.isEmpty()) {
      return baseAttrs;
    }
    AttributesBuilder builder = baseAttrs.toBuilder();
    for (Map.Entry<String, String> entry : metricAttributes.entrySet()) {
      builder.put(entry.getKey(), entry.getValue());
    }
    return builder.build();
  }

  // ---------------------------------------------------------------------------
  // Protected abstract methods (implemented by subclasses)
  // ---------------------------------------------------------------------------

  /** Returns the GenAI operation name (e.g. "chat", "embeddings", "execute_tool"). */
  protected abstract String operationName();

  /** Returns the span name, typically {@code operationName() + " " + model}. */
  protected abstract String spanName();

  /** Applies all operation-specific attributes to the span. Called during finish. */
  protected abstract void applyAttributes();

  /** Builds the base metric attributes for histograms. */
  protected abstract Attributes buildMetricAttributes();

  /** Returns the input token count, or {@code -1} if not applicable / not set. */
  protected abstract long getInputTokens();

  /** Returns the output token count, or {@code -1} if not applicable / not set. */
  protected abstract long getOutputTokens();

  // ---------------------------------------------------------------------------
  // Private lifecycle implementation
  // ---------------------------------------------------------------------------

  private void finish(
      @Nullable String errorType, @Nullable String errorMessage, @Nullable Throwable errorCause) {
    if (finished) {
      return;
    }
    finished = true;
    try {
      if (errorType != null) {
        this.errorType = errorType;
        span.setStatus(StatusCode.ERROR, errorMessage != null ? errorMessage : "");
        span.setAttribute(ErrorIncubatingAttributes.ERROR_TYPE, errorType);
      }
      applyAttributes();
      for (Map.Entry<String, String> entry : extraAttributes.entrySet()) {
        span.setAttribute(entry.getKey(), entry.getValue());
      }
      this.duration = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0;
      handler.recordMetrics(this);
      if (errorType != null) {
        handler.emitExceptionEvent(this, errorType, errorMessage, errorCause);
      }
      handler.finalizeCompletion(this);
    } catch (Throwable t) {
      logger.log(Level.FINE, "Error finalizing invocation telemetry", t);
    } finally {
      try {
        scope.close();
      } catch (Throwable t) {
        logger.log(Level.FINE, "Error closing scope", t);
      }
      try {
        span.end();
      } catch (Throwable t) {
        logger.log(Level.FINE, "Error ending span", t);
      }
    }
  }
}
