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

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;

import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI tool execution.
 *
 * <p>SpanKind is {@code INTERNAL} per {@code span.gen_ai.execute_tool.internal}. The handler sets
 * this when creating the span.
 *
 * <p>Tool arguments and results are only captured on the span when content capturing is enabled via
 * {@link GenAiConfigUtil#shouldCaptureContentOnSpans()}.
 */
public final class ToolInvocation extends GenAiInvocation {

  private final String name;
  private final @Nullable String toolCallId;
  private final @Nullable String toolType;
  private final @Nullable String toolDescription;

  private @Nullable Value<?> arguments;
  private @Nullable Value<?> toolResult;

  ToolInvocation(
      GenAiTelemetryHandler handler,
      Span span,
      Scope scope,
      String name,
      @Nullable String toolCallId,
      @Nullable String toolType,
      @Nullable String toolDescription) {
    super(handler, span, scope);
    this.name = name;
    this.toolCallId = toolCallId;
    this.toolType = toolType;
    this.toolDescription = toolDescription;
  }

  // ---------------------------------------------------------------------------
  // Setters
  // ---------------------------------------------------------------------------

  /** Sets the tool call arguments as a string (opt-in content, requires content capturing). */
  public void setArguments(@Nullable String arguments) {
    this.arguments = arguments != null ? Value.of(arguments) : null;
  }

  /** Sets the tool call arguments as a structured Value (opt-in content). */
  public void setArguments(@Nullable Value<?> arguments) {
    this.arguments = arguments;
  }

  /** Sets the tool execution result as a string (opt-in content, requires content capturing). */
  public void setToolResult(@Nullable String toolResult) {
    this.toolResult = toolResult != null ? Value.of(toolResult) : null;
  }

  /** Sets the tool execution result as a structured Value (opt-in content). */
  public void setToolResult(@Nullable Value<?> toolResult) {
    this.toolResult = toolResult;
  }

  // ---------------------------------------------------------------------------
  // Package-private getters
  // ---------------------------------------------------------------------------

  String getName() {
    return name;
  }

  @Nullable String getToolCallId() {
    return toolCallId;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return "execute_tool";
  }

  @Override
  protected String spanKindValue() {
    return GenAiSpanKindValues.TOOL;
  }

  @Override
  protected String spanName() {
    return "execute_tool " + name;
  }

  @Override
  protected void applyAttributes() {
    span.setAttribute(GEN_AI_OPERATION_NAME, operationName());
    span.setAttribute(GEN_AI_TOOL_NAME, name);
    if (toolCallId != null) {
      span.setAttribute(GEN_AI_TOOL_CALL_ID, toolCallId);
    }
    if (toolType != null) {
      span.setAttribute(GEN_AI_TOOL_TYPE, toolType);
    }
    if (toolDescription != null) {
      span.setAttribute(GEN_AI_TOOL_DESCRIPTION, toolDescription);
    }

    // Tool arguments and result are opt-in content
    if (GenAiConfigUtil.shouldCaptureContentOnSpans()) {
      if (arguments != null) {
        span.setAttribute(GenAiAttributes.GEN_AI_TOOL_CALL_ARGUMENTS, arguments);
      }
      if (toolResult != null) {
        span.setAttribute(GenAiAttributes.GEN_AI_TOOL_CALL_RESULT, toolResult);
      }
    }
  }

  @Override
  protected Attributes buildMetricAttributes() {
    AttributesBuilder builder = Attributes.builder();
    builder.put(GEN_AI_OPERATION_NAME, operationName());
    builder.put(GEN_AI_TOOL_NAME, name);
    if (errorType != null) {
      builder.put(ErrorIncubatingAttributes.ERROR_TYPE, errorType);
    }
    return builder.build();
  }

  @Override
  protected long getInputTokens() {
    return -1;
  }

  @Override
  protected long getOutputTokens() {
    return -1;
  }
}
