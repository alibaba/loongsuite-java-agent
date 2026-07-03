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

import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.NO_CONTENT;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*;
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_PORT;

import com.alibaba.loongsuite.otel.util.genai.stream.StreamMetricsCapable;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Main entry point for GenAI telemetry instrumentation.
 *
 * <p>Creates typed invocations (inference, embedding, tool, agent, workflow) that manage spans,
 * metrics, events, and completion hooks. Each factory method starts a span, opens a scope, and
 * returns an {@link AutoCloseable} invocation that finalizes telemetry when closed.
 *
 * <h3>Usage example:</h3>
 *
 * <pre>{@code
 * GenAiTelemetryHandler handler = GenAiTelemetryHandler.create(openTelemetry);
 *
 * try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
 *     inv.setInputMessages(messages);
 *     // ... call the model ...
 *     inv.setOutputMessages(outputs);
 *     inv.setInputTokens(100L);
 *     inv.setOutputTokens(50L);
 * }
 * }</pre>
 */
public final class GenAiTelemetryHandler {

  private static final String INSTRUMENTATION_NAME = "com.alibaba.loongsuite.otel.util.genai";
  private static final String INSTRUMENTATION_VERSION = "0.1.0";
  private static final String SCHEMA_URL = "https://opentelemetry.io/schemas/1.41.1";

  private final Tracer tracer;
  private final InvocationMetricsRecorder metricsRecorder;
  private final Logger eventLogger;
  private final CompletionHook completionHook;
  private final boolean captureContentEnabled;

  private GenAiTelemetryHandler(
      Tracer tracer,
      InvocationMetricsRecorder metricsRecorder,
      Logger eventLogger,
      CompletionHook completionHook) {
    this.tracer = tracer;
    this.metricsRecorder = metricsRecorder;
    this.eventLogger = eventLogger;
    this.completionHook = new SafeCompletionHook(completionHook);
    this.captureContentEnabled =
        GenAiConfigUtil.getContentCapturingMode() != NO_CONTENT
            || !(completionHook instanceof NoOpCompletionHook);
  }

  // ---------------------------------------------------------------------------
  // Static factories
  // ---------------------------------------------------------------------------

  private static volatile @Nullable GenAiTelemetryHandler defaultInstance;

  /**
   * Returns a shared singleton handler backed by the given {@link OpenTelemetry} instance.
   *
   * <p>On first call, creates the instance; subsequent calls return the same object (matching
   * Python's {@code get_telemetry_handler()} singleton semantics). The completion hook is loaded
   * from environment configuration.
   */
  public static GenAiTelemetryHandler getDefault(OpenTelemetry openTelemetry) {
    GenAiTelemetryHandler instance = defaultInstance;
    if (instance == null) {
      synchronized (GenAiTelemetryHandler.class) {
        instance = defaultInstance;
        if (instance == null) {
          instance = create(openTelemetry);
          defaultInstance = instance;
        }
      }
    }
    return instance;
  }

  /**
   * Creates a handler with default configuration, loading the completion hook from the environment.
   */
  public static GenAiTelemetryHandler create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new builder for customizing the handler. */
  public static Builder builder(OpenTelemetry openTelemetry) {
    return new Builder(openTelemetry);
  }

  // ---------------------------------------------------------------------------
  // Invocation factory methods
  // ---------------------------------------------------------------------------

  /** Creates an inference invocation with default operation name {@code "chat"}. */
  public InferenceInvocation inference(String provider, @Nullable String requestModel) {
    return inference(provider, requestModel, null, null, null);
  }

  /**
   * Creates an inference invocation with full parameters.
   *
   * @param provider the GenAI provider name (e.g. "openai", "dashscope")
   * @param requestModel the requested model name
   * @param serverAddress the server address, or {@code null}
   * @param serverPort the server port, or {@code null}
   * @param operationName custom operation name, defaults to "chat" if {@code null}
   */
  public InferenceInvocation inference(
      String provider,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      @Nullable String operationName) {
    String opName = operationName != null ? operationName : "chat";
    String spanName = requestModel != null ? opName + " " + requestModel : opName;
    Span span =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(GEN_AI_OPERATION_NAME, opName)
            .setAttribute(GEN_AI_PROVIDER_NAME, provider)
            .startSpan();
    Scope scope = span.makeCurrent();
    return new InferenceInvocation(
        this, span, scope, provider, requestModel, serverAddress, serverPort, operationName);
  }

  /** Creates an embedding invocation with minimal parameters. */
  public EmbeddingInvocation embedding(String provider, @Nullable String requestModel) {
    return embedding(provider, requestModel, null, null);
  }

  /**
   * Creates an embedding invocation with full parameters.
   *
   * @param provider the GenAI provider name
   * @param requestModel the requested model name
   * @param serverAddress the server address, or {@code null}
   * @param serverPort the server port, or {@code null}
   */
  public EmbeddingInvocation embedding(
      String provider,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    String spanName = requestModel != null ? "embeddings " + requestModel : "embeddings";
    Span span =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(GEN_AI_OPERATION_NAME, "embeddings")
            .setAttribute(GEN_AI_PROVIDER_NAME, provider)
            .startSpan();
    Scope scope = span.makeCurrent();
    return new EmbeddingInvocation(
        this, span, scope, provider, requestModel, serverAddress, serverPort);
  }

  /** Creates a tool execution invocation with just the tool name. */
  public ToolInvocation tool(String name) {
    return tool(name, null, null, null);
  }

  /**
   * Creates a tool execution invocation.
   *
   * <p>Per semconv {@code span.gen_ai.execute_tool.internal}, tool spans do not include {@code
   * gen_ai.provider.name}.
   *
   * @param name the tool name
   * @param toolCallId the tool call ID from the model response, or {@code null}
   * @param toolType the tool type (e.g. "function"), or {@code null}
   * @param toolDescription a human-readable description of the tool, or {@code null}
   */
  public ToolInvocation tool(
      String name,
      @Nullable String toolCallId,
      @Nullable String toolType,
      @Nullable String toolDescription) {
    String spanName = "execute_tool " + name;
    Span span =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(GEN_AI_OPERATION_NAME, "execute_tool")
            .setAttribute(GEN_AI_TOOL_NAME, name)
            .startSpan();
    Scope scope = span.makeCurrent();
    return new ToolInvocation(this, span, scope, name, toolCallId, toolType, toolDescription);
  }

  /**
   * Creates a workflow invocation.
   *
   * <p>Per semconv {@code span.gen_ai.invoke_workflow.internal}, workflow spans do not include
   * {@code gen_ai.provider.name}.
   *
   * @param name the workflow name, or {@code null}
   */
  public WorkflowInvocation workflow(@Nullable String name) {
    String spanName = name != null ? "invoke_workflow " + name : "invoke_workflow";
    Span span =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(GEN_AI_OPERATION_NAME, "invoke_workflow")
            .startSpan();
    Scope scope = span.makeCurrent();
    return new WorkflowInvocation(this, span, scope, name);
  }

  /**
   * Creates a local agent invocation (SpanKind.INTERNAL).
   *
   * @param provider the GenAI provider name
   * @param requestModel the requested model name
   * @param agentName the agent name, or {@code null}
   */
  public AgentInvocation invokeLocalAgent(
      String provider, @Nullable String requestModel, @Nullable String agentName) {
    String spanName = agentName != null ? "invoke_agent " + agentName : "invoke_agent";
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(GEN_AI_OPERATION_NAME, "invoke_agent")
            .setAttribute(GEN_AI_PROVIDER_NAME, provider);
    if (requestModel != null) {
      spanBuilder.setAttribute(GEN_AI_REQUEST_MODEL, requestModel);
    }
    Span span = spanBuilder.startSpan();
    Scope scope = span.makeCurrent();
    return new AgentInvocation(
        this, span, scope, provider, requestModel, agentName, false, null, null);
  }

  /**
   * Creates a remote agent invocation (SpanKind.CLIENT).
   *
   * @param provider the GenAI provider name
   * @param requestModel the requested model name
   * @param agentName the agent name, or {@code null}
   * @param serverAddress the server address, or {@code null}
   * @param serverPort the server port, or {@code null}
   */
  public AgentInvocation invokeRemoteAgent(
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    String spanName = agentName != null ? "invoke_agent " + agentName : "invoke_agent";
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(GEN_AI_OPERATION_NAME, "invoke_agent")
            .setAttribute(GEN_AI_PROVIDER_NAME, provider);
    if (requestModel != null) {
      spanBuilder.setAttribute(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (serverAddress != null) {
      spanBuilder.setAttribute(SERVER_ADDRESS, serverAddress);
    }
    if (serverPort != null) {
      spanBuilder.setAttribute(SERVER_PORT, (long) serverPort);
    }
    Span span = spanBuilder.startSpan();
    Scope scope = span.makeCurrent();
    return new AgentInvocation(
        this, span, scope, provider, requestModel, agentName, true, serverAddress, serverPort);
  }

  /**
   * Creates a retrieval invocation with minimal parameters.
   *
   * @param provider the GenAI provider name, or {@code null}
   * @param dataSourceId the data source identifier, or {@code null}
   */
  public RetrievalInvocation retrieval(@Nullable String provider, @Nullable String dataSourceId) {
    return retrieval(provider, dataSourceId, null, null);
  }

  /**
   * Creates a retrieval invocation with server parameters.
   *
   * @param provider the GenAI provider name, or {@code null}
   * @param dataSourceId the data source identifier, or {@code null}
   * @param serverAddress the server address, or {@code null}
   * @param serverPort the server port, or {@code null}
   */
  public RetrievalInvocation retrieval(
      @Nullable String provider,
      @Nullable String dataSourceId,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    return retrieval(provider, dataSourceId, null, serverAddress, serverPort);
  }

  /**
   * Creates a retrieval invocation with full parameters.
   *
   * @param provider the GenAI provider name, or {@code null}
   * @param dataSourceId the data source identifier, or {@code null}
   * @param requestModel the requested model name, or {@code null}
   * @param serverAddress the server address, or {@code null}
   * @param serverPort the server port, or {@code null}
   */
  public RetrievalInvocation retrieval(
      @Nullable String provider,
      @Nullable String dataSourceId,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    String spanName = dataSourceId != null ? "retrieval " + dataSourceId : "retrieval";
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(GEN_AI_OPERATION_NAME, "retrieval");
    if (provider != null) {
      spanBuilder.setAttribute(GEN_AI_PROVIDER_NAME, provider);
    }
    if (requestModel != null) {
      spanBuilder.setAttribute(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (serverAddress != null) {
      spanBuilder.setAttribute(SERVER_ADDRESS, serverAddress);
    }
    if (serverPort != null) {
      spanBuilder.setAttribute(SERVER_PORT, (long) serverPort);
    }
    Span span = spanBuilder.startSpan();
    Scope scope = span.makeCurrent();
    return new RetrievalInvocation(
        this, span, scope, provider, dataSourceId, requestModel, serverAddress, serverPort);
  }

  /**
   * Creates a create-agent invocation with minimal parameters.
   *
   * @param provider the GenAI provider name
   * @param requestModel the requested model name, or {@code null}
   * @param agentName the agent name, or {@code null}
   */
  public CreateAgentInvocation createAgent(
      String provider, @Nullable String requestModel, @Nullable String agentName) {
    return createAgent(provider, requestModel, agentName, null, null);
  }

  /**
   * Creates a create-agent invocation with full parameters.
   *
   * @param provider the GenAI provider name
   * @param requestModel the requested model name, or {@code null}
   * @param agentName the agent name, or {@code null}
   * @param serverAddress the server address, or {@code null}
   * @param serverPort the server port, or {@code null}
   */
  public CreateAgentInvocation createAgent(
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    String spanName = agentName != null ? "create_agent " + agentName : "create_agent";
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(GEN_AI_OPERATION_NAME, "create_agent")
            .setAttribute(GEN_AI_PROVIDER_NAME, provider);
    if (requestModel != null) {
      spanBuilder.setAttribute(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (serverAddress != null) {
      spanBuilder.setAttribute(SERVER_ADDRESS, serverAddress);
    }
    if (serverPort != null) {
      spanBuilder.setAttribute(SERVER_PORT, (long) serverPort);
    }
    Span span = spanBuilder.startSpan();
    Scope scope = span.makeCurrent();
    return new CreateAgentInvocation(
        this, span, scope, provider, requestModel, agentName, serverAddress, serverPort);
  }

  /**
   * Returns whether content should be captured.
   *
   * <p>Content is captured when the content capturing mode requires it, or when a real completion
   * hook is configured (not a no-op), matching Python {@code
   * TelemetryHandler.should_capture_content}.
   */
  public boolean shouldCaptureContent() {
    return captureContentEnabled;
  }

  // ---------------------------------------------------------------------------
  // Callback-style API (auto-captures exceptions, like Python's context manager)
  // ---------------------------------------------------------------------------

  /**
   * Executes the given action within an inference invocation, automatically calling {@link
   * GenAiInvocation#fail(Throwable)} if the action throws.
   *
   * <p>This is the Java equivalent of Python's {@code with handler.llm(invocation)} context manager
   * pattern — exceptions are always captured on the span.
   *
   * <pre>{@code
   * handler.inferenceRun("openai", "gpt-4o", inv -> {
   *     inv.setInputMessages(messages);
   *     var response = client.chat(request);
   *     inv.setOutputMessages(outputs);
   *     inv.setInputTokens(response.usage().promptTokens());
   * });
   * }</pre>
   */
  public void inferenceRun(
      String provider,
      @Nullable String requestModel,
      InvocationAction<InferenceInvocation> action) {
    InferenceInvocation inv = inference(provider, requestModel);
    runWithInvocation(inv, action);
  }

  /** Callback-style embedding invocation. */
  public void embeddingRun(
      String provider,
      @Nullable String requestModel,
      InvocationAction<EmbeddingInvocation> action) {
    EmbeddingInvocation inv = embedding(provider, requestModel);
    runWithInvocation(inv, action);
  }

  /** Callback-style tool invocation. */
  public void toolRun(String name, InvocationAction<ToolInvocation> action) {
    ToolInvocation inv = tool(name);
    runWithInvocation(inv, action);
  }

  /** Callback-style local agent invocation. */
  public void localAgentRun(
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      InvocationAction<AgentInvocation> action) {
    AgentInvocation inv = invokeLocalAgent(provider, requestModel, agentName);
    runWithInvocation(inv, action);
  }

  /** Callback-style remote agent invocation. */
  public void remoteAgentRun(
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      InvocationAction<AgentInvocation> action) {
    AgentInvocation inv =
        invokeRemoteAgent(provider, requestModel, agentName, serverAddress, serverPort);
    runWithInvocation(inv, action);
  }

  /** Callback-style workflow invocation. */
  public void workflowRun(@Nullable String name, InvocationAction<WorkflowInvocation> action) {
    WorkflowInvocation inv = workflow(name);
    runWithInvocation(inv, action);
  }

  /** Callback-style retrieval invocation. */
  public void retrievalRun(
      @Nullable String provider,
      @Nullable String dataSourceId,
      InvocationAction<RetrievalInvocation> action) {
    retrievalRun(provider, dataSourceId, null, null, action);
  }

  /** Callback-style retrieval invocation with server parameters. */
  public void retrievalRun(
      @Nullable String provider,
      @Nullable String dataSourceId,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      InvocationAction<RetrievalInvocation> action) {
    RetrievalInvocation inv = retrieval(provider, dataSourceId, serverAddress, serverPort);
    runWithInvocation(inv, action);
  }

  /** Callback-style create-agent invocation. */
  public void createAgentRun(
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      InvocationAction<CreateAgentInvocation> action) {
    CreateAgentInvocation inv = createAgent(provider, requestModel, agentName);
    runWithInvocation(inv, action);
  }

  private <T extends GenAiInvocation> void runWithInvocation(T inv, InvocationAction<T> action) {
    try {
      action.execute(inv);
      inv.stop();
    } catch (Throwable t) {
      inv.fail(t);
      if (t instanceof RuntimeException) throw (RuntimeException) t;
      if (t instanceof Error) throw (Error) t;
      throw new RuntimeException(t);
    }
  }

  /** Action to execute within an invocation, may throw any exception. */
  @FunctionalInterface
  public interface InvocationAction<T extends GenAiInvocation> {
    void execute(T invocation) throws Throwable;
  }

  // ---------------------------------------------------------------------------
  // Package-private methods called by GenAiInvocation.finish()
  // ---------------------------------------------------------------------------

  /**
   * Records duration and token usage metrics for the completed invocation.
   *
   * <p>Called by {@link GenAiInvocation#finish} after attributes have been applied.
   */
  void recordMetrics(GenAiInvocation invocation) {
    Attributes metricAttrs = invocation.getAllMetricAttributes();
    Context ctx = Context.current();

    metricsRecorder.recordDuration(invocation.getDuration(), metricAttrs, ctx);

    long inputTokens = invocation.getInputTokens();
    if (inputTokens >= 0) {
      Attributes tokenAttrs = metricAttrs.toBuilder().put(GEN_AI_TOKEN_TYPE, "input").build();
      metricsRecorder.recordTokenUsage(inputTokens, tokenAttrs, ctx);
    }
    long outputTokens = invocation.getOutputTokens();
    if (outputTokens >= 0) {
      Attributes tokenAttrs = metricAttrs.toBuilder().put(GEN_AI_TOKEN_TYPE, "output").build();
      metricsRecorder.recordTokenUsage(outputTokens, tokenAttrs, ctx);
    }

    recordStreamingMetrics(invocation, metricAttrs, ctx);
  }

  private void recordStreamingMetrics(
      GenAiInvocation invocation, Attributes metricAttrs, Context ctx) {
    if (!(invocation instanceof StreamMetricsCapable)) {
      return;
    }
    StreamMetricsCapable streamCapable = (StreamMetricsCapable) invocation;
    Double ttfc = streamCapable.getTimeToFirstChunk();
    if (ttfc != null) {
      metricsRecorder.recordTimeToFirstChunk(ttfc, metricAttrs, ctx);
    }
    for (double delay : streamCapable.getInterChunkDelays()) {
      metricsRecorder.recordTimePerOutputChunk(delay, metricAttrs, ctx);
    }
  }

  /**
   * Runs completion hooks before span content attributes are applied.
   *
   * <p>Multimodal hooks replace {@code BlobPart} values with {@code UriPart} references; upload
   * hooks may stamp {@code *_ref} attributes on the span and pending event record.
   */
  void invokeCompletionHooks(GenAiInvocation invocation) {
    if (invocation instanceof InferenceInvocation) {
      InferenceInvocation inference = (InferenceInvocation) invocation;
      MutableEventLogRecord pendingEvent = null;
      if (GenAiConfigUtil.shouldEmitEvent() && GenAiConfigUtil.isExperimentalMode()) {
        pendingEvent =
            new MutableEventLogRecord(io.opentelemetry.api.common.Attributes.builder().build());
      }
      completionHook.onCompletion(
          MutableCompletionHookContext.forInference(inference, pendingEvent));
      inference.setPendingEvent(pendingEvent);
    } else if (invocation instanceof AgentInvocation) {
      AgentInvocation agent = (AgentInvocation) invocation;
      completionHook.onCompletion(MutableCompletionHookContext.forAgent(agent, null));
    } else if (invocation instanceof WorkflowInvocation) {
      WorkflowInvocation workflow = (WorkflowInvocation) invocation;
      completionHook.onCompletion(MutableCompletionHookContext.forWorkflow(workflow, null));
    }
  }

  /** Emits the inference details event after span attributes have been applied. */
  void emitInferenceEventIfNeeded(GenAiInvocation invocation) {
    if (!(invocation instanceof InferenceInvocation)) {
      return;
    }
    InferenceInvocation inference = (InferenceInvocation) invocation;
    if (!GenAiConfigUtil.shouldEmitEvent() || !GenAiConfigUtil.isExperimentalMode()) {
      return;
    }
    Attributes contentAttrs =
        inference.buildEventAttributes(inference.errorType, inference.getExtraAttributes());
    MutableEventLogRecord pendingEvent = inference.getPendingEvent();
    if (pendingEvent != null) {
      AttributesBuilder merged = contentAttrs.toBuilder();
      merged.putAll(pendingEvent.getAttributes());
      emitInferenceEvent(inference, new MutableEventLogRecord(merged.build()));
      return;
    }
    emitInferenceEvent(inference, new MutableEventLogRecord(contentAttrs));
  }

  private void emitInferenceEvent(InferenceInvocation invocation, EventLogRecord pendingEvent) {
    LogRecordBuilder logRecord = eventLogger.logRecordBuilder();
    logRecord.setAllAttributes(pendingEvent.getAttributes());
    logRecord.setEventName("gen_ai.client.inference.operation.details");
    logRecord.setContext(
        invocation.span.getSpanContext().isValid() ? Context.current() : Context.root());
    logRecord.emit();
  }

  // ---------------------------------------------------------------------------
  // Public event emission methods
  // ---------------------------------------------------------------------------

  /**
   * Emits a {@code gen_ai.client.operation.exception} event per semconv v1.41.1.
   *
   * <p>This is called automatically by {@link GenAiInvocation#finish} when there is an error. Can
   * also be called manually for custom error scenarios.
   */
  void emitExceptionEvent(
      GenAiInvocation invocation,
      String errorType,
      @Nullable String message,
      @Nullable Throwable cause) {
    AttributesBuilder eventAttrs = Attributes.builder();
    eventAttrs.put(ExceptionIncubatingAttributes.EXCEPTION_TYPE, errorType);
    if (message != null) {
      eventAttrs.put(ExceptionIncubatingAttributes.EXCEPTION_MESSAGE, message);
    }
    String stacktrace = stackTraceToString(cause);
    if (stacktrace != null) {
      eventAttrs.put(ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE, stacktrace);
    }

    LogRecordBuilder logRecord = eventLogger.logRecordBuilder();
    logRecord.setAllAttributes(eventAttrs.build());
    logRecord.setEventName("gen_ai.client.operation.exception");
    logRecord.setSeverity(Severity.WARN);
    logRecord.setContext(
        invocation.span.getSpanContext().isValid() ? Context.current() : Context.root());
    logRecord.emit();
  }

  /**
   * Emits a {@code gen_ai.evaluation.result} event per semconv v1.41.1.
   *
   * @param evaluationName the evaluation name (required)
   * @param scoreValue the numeric score, or {@code null}
   * @param scoreLabel the label-based score, or {@code null}
   * @param explanation optional explanation
   * @param responseId the gen_ai.response.id to correlate with, or {@code null}
   */
  public void emitEvaluationResult(
      String evaluationName,
      @Nullable Double scoreValue,
      @Nullable String scoreLabel,
      @Nullable String explanation,
      @Nullable String responseId) {
    emitEvaluationResult(evaluationName, scoreValue, scoreLabel, explanation, responseId, null);
  }

  /**
   * Emits a {@code gen_ai.evaluation.result} event, including {@code error.type} when the
   * evaluation operation failed.
   *
   * @param errorType describes the evaluation failure, or {@code null} on success
   */
  public void emitEvaluationResult(
      String evaluationName,
      @Nullable Double scoreValue,
      @Nullable String scoreLabel,
      @Nullable String explanation,
      @Nullable String responseId,
      @Nullable String errorType) {
    AttributesBuilder eventAttrs = Attributes.builder();
    eventAttrs.put(GEN_AI_EVALUATION_NAME, evaluationName);
    if (errorType != null) {
      eventAttrs.put(ErrorIncubatingAttributes.ERROR_TYPE, errorType);
    }
    if (scoreValue != null) {
      eventAttrs.put(GEN_AI_EVALUATION_SCORE_VALUE, scoreValue);
    }
    if (scoreLabel != null) {
      eventAttrs.put(GEN_AI_EVALUATION_SCORE_LABEL, scoreLabel);
    }
    if (explanation != null) {
      eventAttrs.put(GEN_AI_EVALUATION_EXPLANATION, explanation);
    }
    if (responseId != null) {
      eventAttrs.put(GEN_AI_RESPONSE_ID, responseId);
    }

    LogRecordBuilder logRecord = eventLogger.logRecordBuilder();
    logRecord.setAllAttributes(eventAttrs.build());
    logRecord.setEventName("gen_ai.evaluation.result");
    logRecord.emit();
  }

  @Nullable
  private static String stackTraceToString(@Nullable Throwable cause) {
    if (cause == null) {
      return null;
    }
    StringWriter writer = new StringWriter();
    cause.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  // ---------------------------------------------------------------------------
  // Builder
  // ---------------------------------------------------------------------------

  /** Builder for {@link GenAiTelemetryHandler}. */
  public static final class Builder {

    private final OpenTelemetry openTelemetry;
    private @Nullable CompletionHook completionHook;

    private Builder(OpenTelemetry openTelemetry) {
      this.openTelemetry = openTelemetry;
    }

    /**
     * Sets a custom {@link CompletionHook}. If not set, the hook is loaded via {@link
     * CompletionHookLoader}.
     */
    public Builder setCompletionHook(CompletionHook hook) {
      this.completionHook = hook;
      return this;
    }

    /** Builds the handler. */
    public GenAiTelemetryHandler build() {
      Tracer tracer =
          openTelemetry
              .getTracerProvider()
              .tracerBuilder(INSTRUMENTATION_NAME)
              .setInstrumentationVersion(INSTRUMENTATION_VERSION)
              .setSchemaUrl(SCHEMA_URL)
              .build();

      Meter meter =
          openTelemetry
              .getMeterProvider()
              .meterBuilder(INSTRUMENTATION_NAME)
              .setInstrumentationVersion(INSTRUMENTATION_VERSION)
              .setSchemaUrl(SCHEMA_URL)
              .build();

      Logger logger =
          openTelemetry
              .getLogsBridge()
              .loggerBuilder(INSTRUMENTATION_NAME)
              .setInstrumentationVersion(INSTRUMENTATION_VERSION)
              .build();

      InvocationMetricsRecorder recorder = new InvocationMetricsRecorder(meter);
      CompletionHook hook = completionHook != null ? completionHook : CompletionHookLoader.load();

      return new GenAiTelemetryHandler(tracer, recorder, logger, hook);
    }
  }
}
