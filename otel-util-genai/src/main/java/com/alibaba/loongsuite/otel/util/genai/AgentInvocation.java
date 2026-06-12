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

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI agent invocation.
 *
 * <p>SpanKind is {@code CLIENT} for remote agents and {@code INTERNAL} for local agents. The
 * handler sets this when creating the span based on the {@code remote} flag.
 *
 * <p>Attributes follow {@code span.gen_ai.invoke_agent.client} / {@code
 * span.gen_ai.invoke_agent.internal} — inference-only attributes such as {@code
 * gen_ai.response.model} are intentionally excluded.
 */
public final class AgentInvocation extends GenAiInvocation {

  private final String provider;
  private final @Nullable String requestModel;
  private final @Nullable String agentName;
  private final boolean remote;
  private final @Nullable String serverAddress;
  private final @Nullable Integer serverPort;

  // Agent-specific attributes
  private @Nullable String agentId;
  private @Nullable String agentDescription;
  private @Nullable String agentVersion;
  private @Nullable String conversationId;
  private @Nullable String dataSourceId;
  private @Nullable String outputType;

  // Request parameters (invoke_agent.common)
  private @Nullable Double temperature;
  private @Nullable Double topP;
  private @Nullable Double frequencyPenalty;
  private @Nullable Double presencePenalty;
  private @Nullable Long maxTokens;
  private @Nullable List<String> stopSequences;
  private @Nullable Long choiceCount;
  private @Nullable Long seed;

  // Usage
  private @Nullable Long inputTokens;
  private @Nullable Long outputTokens;
  private @Nullable Long cacheCreationInputTokens;
  private @Nullable Long cacheReadInputTokens;

  // Content
  private List<InputMessage> inputMessages = Collections.emptyList();
  private List<OutputMessage> outputMessages = Collections.emptyList();
  private List<MessagePart> systemInstruction = Collections.emptyList();
  private List<ToolDefinition> toolDefinitions = Collections.emptyList();

  // Response
  private @Nullable List<String> finishReasons;

  AgentInvocation(
      GenAiTelemetryHandler handler,
      Span span,
      Scope scope,
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      boolean remote,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    super(handler, span, scope);
    this.provider = provider;
    this.requestModel = requestModel;
    this.agentName = agentName;
    this.remote = remote;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
  }

  // ---------------------------------------------------------------------------
  // Setters — agent-specific
  // ---------------------------------------------------------------------------

  public void setAgentId(@Nullable String agentId) {
    this.agentId = agentId;
  }

  public void setAgentDescription(@Nullable String agentDescription) {
    this.agentDescription = agentDescription;
  }

  public void setAgentVersion(@Nullable String agentVersion) {
    this.agentVersion = agentVersion;
  }

  public void setConversationId(@Nullable String conversationId) {
    this.conversationId = conversationId;
  }

  public void setDataSourceId(@Nullable String dataSourceId) {
    this.dataSourceId = dataSourceId;
  }

  public void setOutputType(@Nullable String outputType) {
    this.outputType = outputType;
  }

  // ---------------------------------------------------------------------------
  // Setters — request parameters
  // ---------------------------------------------------------------------------

  public void setTemperature(@Nullable Double temperature) {
    this.temperature = temperature;
  }

  public void setTopP(@Nullable Double topP) {
    this.topP = topP;
  }

  public void setFrequencyPenalty(@Nullable Double frequencyPenalty) {
    this.frequencyPenalty = frequencyPenalty;
  }

  public void setPresencePenalty(@Nullable Double presencePenalty) {
    this.presencePenalty = presencePenalty;
  }

  public void setMaxTokens(@Nullable Long maxTokens) {
    this.maxTokens = maxTokens;
  }

  public void setStopSequences(@Nullable List<String> stopSequences) {
    this.stopSequences = stopSequences;
  }

  public void setChoiceCount(@Nullable Long choiceCount) {
    this.choiceCount = choiceCount;
  }

  public void setSeed(@Nullable Long seed) {
    this.seed = seed;
  }

  // ---------------------------------------------------------------------------
  // Setters — usage
  // ---------------------------------------------------------------------------

  public void setInputTokens(@Nullable Long inputTokens) {
    this.inputTokens = inputTokens;
  }

  public void setOutputTokens(@Nullable Long outputTokens) {
    this.outputTokens = outputTokens;
  }

  public void setCacheCreationInputTokens(@Nullable Long cacheCreationInputTokens) {
    this.cacheCreationInputTokens = cacheCreationInputTokens;
  }

  public void setCacheReadInputTokens(@Nullable Long cacheReadInputTokens) {
    this.cacheReadInputTokens = cacheReadInputTokens;
  }

  // ---------------------------------------------------------------------------
  // Setters — content
  // ---------------------------------------------------------------------------

  public void setInputMessages(List<InputMessage> inputMessages) {
    this.inputMessages = inputMessages;
  }

  public void setOutputMessages(List<OutputMessage> outputMessages) {
    this.outputMessages = outputMessages;
  }

  public void setSystemInstruction(List<MessagePart> systemInstruction) {
    this.systemInstruction = systemInstruction;
  }

  public void setToolDefinitions(List<ToolDefinition> toolDefinitions) {
    this.toolDefinitions = toolDefinitions;
  }

  // ---------------------------------------------------------------------------
  // Setters — response
  // ---------------------------------------------------------------------------

  public void setFinishReasons(@Nullable List<String> finishReasons) {
    this.finishReasons = finishReasons;
  }

  // ---------------------------------------------------------------------------
  // Package-private getters (for handler)
  // ---------------------------------------------------------------------------

  boolean isRemote() {
    return remote;
  }

  @Nullable
  String getAgentName() {
    return agentName;
  }

  List<InputMessage> getInputMessages() {
    return inputMessages;
  }

  List<OutputMessage> getOutputMessages() {
    return outputMessages;
  }

  List<MessagePart> getSystemInstruction() {
    return systemInstruction;
  }

  List<ToolDefinition> getToolDefinitions() {
    return toolDefinitions;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return "invoke_agent";
  }

  @Override
  protected String spanName() {
    return agentName != null ? "invoke_agent " + agentName : "invoke_agent";
  }

  @Override
  protected void applyAttributes() {
    SpanAttributeHelper.applyCommonAttributes(
        span,
        operationName(),
        provider,
        requestModel,
        remote ? serverAddress : null,
        remote ? serverPort : null);

    if (agentName != null) {
      span.setAttribute(GEN_AI_AGENT_NAME, agentName);
    }
    if (agentId != null) {
      span.setAttribute(GEN_AI_AGENT_ID, agentId);
    }
    if (agentDescription != null) {
      span.setAttribute(GEN_AI_AGENT_DESCRIPTION, agentDescription);
    }
    if (agentVersion != null) {
      span.setAttribute(GEN_AI_AGENT_VERSION, agentVersion);
    }
    if (conversationId != null) {
      span.setAttribute(GEN_AI_CONVERSATION_ID, conversationId);
    }
    if (dataSourceId != null) {
      span.setAttribute(GEN_AI_DATA_SOURCE_ID, dataSourceId);
    }
    if (outputType != null) {
      span.setAttribute(GEN_AI_OUTPUT_TYPE, outputType);
    }

    if (temperature != null) {
      span.setAttribute(GEN_AI_REQUEST_TEMPERATURE, temperature);
    }
    if (topP != null) {
      span.setAttribute(GEN_AI_REQUEST_TOP_P, topP);
    }
    if (frequencyPenalty != null) {
      span.setAttribute(GEN_AI_REQUEST_FREQUENCY_PENALTY, frequencyPenalty);
    }
    if (presencePenalty != null) {
      span.setAttribute(GEN_AI_REQUEST_PRESENCE_PENALTY, presencePenalty);
    }
    if (maxTokens != null) {
      span.setAttribute(GEN_AI_REQUEST_MAX_TOKENS, maxTokens);
    }
    if (stopSequences != null) {
      span.setAttribute(GEN_AI_REQUEST_STOP_SEQUENCES, stopSequences);
    }
    if (choiceCount != null) {
      span.setAttribute(GEN_AI_REQUEST_CHOICE_COUNT, choiceCount);
    }
    if (seed != null) {
      span.setAttribute(GEN_AI_REQUEST_SEED, seed);
    }

    AttributesBuilder contentBuilder = Attributes.builder();
    GenAiContentAttributes.appendInferenceContent(
        contentBuilder, inputMessages, outputMessages, systemInstruction, toolDefinitions, true);
    span.setAllAttributes(contentBuilder.build());

    List<String> resolvedFinishReasons =
        GenAiFinishReasons.resolve(finishReasons, outputMessages);
    if (resolvedFinishReasons != null) {
      span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, resolvedFinishReasons);
    }

    if (inputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, inputTokens);
    }
    if (outputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, outputTokens);
    }
    if (cacheCreationInputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_CACHE_CREATION_INPUT_TOKENS, cacheCreationInputTokens);
    }
    if (cacheReadInputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, cacheReadInputTokens);
    }
  }

  @Override
  protected Attributes buildMetricAttributes() {
    return SpanAttributeHelper.commonMetricAttributes(
            operationName(),
            provider,
            requestModel,
            null,
            remote ? serverAddress : null,
            remote ? serverPort : null,
            errorType)
        .build();
  }

  @Override
  protected long getInputTokens() {
    return inputTokens != null ? inputTokens : -1;
  }

  @Override
  protected long getOutputTokens() {
    return outputTokens != null ? outputTokens : -1;
  }
}
