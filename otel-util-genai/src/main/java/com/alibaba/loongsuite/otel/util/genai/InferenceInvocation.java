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
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_PORT;

import com.alibaba.loongsuite.otel.util.genai.stream.StreamMetricsCapable;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI inference (chat completion) call.
 *
 * <p>SpanKind is {@code CLIENT}. The handler sets this when creating the span.
 */
public final class InferenceInvocation extends GenAiInvocation implements StreamMetricsCapable {

  private final List<Double> interChunkDelays = new ArrayList<>();

  private final String provider;
  private final @Nullable String requestModel;
  private final @Nullable String serverAddress;
  private final @Nullable Integer serverPort;
  private @Nullable String customOperationName;

  // Request attributes
  private List<InputMessage> inputMessages = Collections.emptyList();
  private List<MessagePart> systemInstruction = Collections.emptyList();
  private List<ToolDefinition> toolDefinitions = Collections.emptyList();
  private @Nullable Double temperature;
  private @Nullable Double topP;
  private @Nullable Double frequencyPenalty;
  private @Nullable Double presencePenalty;
  private @Nullable Long maxTokens;
  private @Nullable List<String> stopSequences;
  private @Nullable Long seed;
  private @Nullable Double topK;
  private @Nullable Long choiceCount;
  private @Nullable String outputType;
  private @Nullable Boolean stream;
  private @Nullable Double timeToFirstChunk;
  private @Nullable String conversationId;

  // Response attributes
  private List<OutputMessage> outputMessages = Collections.emptyList();
  private @Nullable String responseModel;
  private @Nullable String responseId;
  private @Nullable List<String> finishReasons;
  private @Nullable Long inputTokens;
  private @Nullable Long outputTokens;
  private @Nullable Long thinkingTokens;
  private @Nullable Long cacheCreationInputTokens;
  private @Nullable Long cacheReadInputTokens;

  InferenceInvocation(
      GenAiTelemetryHandler handler,
      Span span,
      Scope scope,
      String provider,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      @Nullable String operationName) {
    super(handler, span, scope);
    this.provider = provider;
    this.requestModel = requestModel;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
    this.customOperationName = operationName;
  }

  // ---------------------------------------------------------------------------
  // Setters — request attributes
  // ---------------------------------------------------------------------------

  public void setOperationName(@Nullable String operationName) {
    this.customOperationName = operationName;
  }

  public void setInputMessages(List<InputMessage> inputMessages) {
    this.inputMessages = inputMessages;
  }

  public void setSystemInstruction(List<MessagePart> systemInstruction) {
    this.systemInstruction = systemInstruction;
  }

  public void setToolDefinitions(List<ToolDefinition> toolDefinitions) {
    this.toolDefinitions = toolDefinitions;
  }

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

  public void setSeed(@Nullable Long seed) {
    this.seed = seed;
  }

  public void setTopK(@Nullable Double topK) {
    this.topK = topK;
  }

  public void setChoiceCount(@Nullable Long choiceCount) {
    this.choiceCount = choiceCount;
  }

  public void setOutputType(@Nullable String outputType) {
    this.outputType = outputType;
  }

  public void setStream(@Nullable Boolean stream) {
    this.stream = stream;
  }

  public void setTimeToFirstChunk(@Nullable Double timeToFirstChunk) {
    this.timeToFirstChunk = timeToFirstChunk;
  }

  @Override
  public void setTimeToFirstChunk(double seconds) {
    this.timeToFirstChunk = seconds;
  }

  @Override
  public void addInterChunkDelay(double seconds) {
    interChunkDelays.add(seconds);
  }

  @Override
  public List<Double> getInterChunkDelays() {
    return interChunkDelays;
  }

  public void setConversationId(@Nullable String conversationId) {
    this.conversationId = conversationId;
  }

  // ---------------------------------------------------------------------------
  // Setters — response attributes
  // ---------------------------------------------------------------------------

  public void setOutputMessages(List<OutputMessage> outputMessages) {
    this.outputMessages = outputMessages;
  }

  public void setResponseModel(@Nullable String responseModel) {
    this.responseModel = responseModel;
  }

  public void setResponseId(@Nullable String responseId) {
    this.responseId = responseId;
  }

  public void setFinishReasons(@Nullable List<String> finishReasons) {
    this.finishReasons = finishReasons;
  }

  public void setInputTokens(@Nullable Long inputTokens) {
    this.inputTokens = inputTokens;
  }

  public void setOutputTokens(@Nullable Long outputTokens) {
    this.outputTokens = outputTokens;
  }

  public void setThinkingTokens(@Nullable Long thinkingTokens) {
    this.thinkingTokens = thinkingTokens;
  }

  public void setCacheCreationInputTokens(@Nullable Long cacheCreationInputTokens) {
    this.cacheCreationInputTokens = cacheCreationInputTokens;
  }

  public void setCacheReadInputTokens(@Nullable Long cacheReadInputTokens) {
    this.cacheReadInputTokens = cacheReadInputTokens;
  }

  // ---------------------------------------------------------------------------
  // Package-private getters (for handler event emission and metrics)
  // ---------------------------------------------------------------------------

  List<InputMessage> getInputMessages() {
    return inputMessages;
  }

  List<MessagePart> getSystemInstruction() {
    return systemInstruction;
  }

  List<OutputMessage> getOutputMessages() {
    return outputMessages;
  }

  List<ToolDefinition> getToolDefinitions() {
    return toolDefinitions;
  }

  @Nullable String getResponseModel() {
    return responseModel;
  }

  @Nullable String getResponseId() {
    return responseId;
  }

  @Override
  @Nullable
  public Double getTimeToFirstChunk() {
    return timeToFirstChunk;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return customOperationName != null ? customOperationName : "chat";
  }

  @Override
  protected String spanName() {
    return requestModel != null ? operationName() + " " + requestModel : operationName();
  }

  @Override
  protected void applyAttributes() {
    SpanAttributeHelper.applyCommonAttributes(
        span, operationName(), provider, requestModel, serverAddress, serverPort);

    // Request attributes
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
    if (seed != null) {
      span.setAttribute(GEN_AI_REQUEST_SEED, seed);
    }
    if (topK != null) {
      span.setAttribute(GEN_AI_REQUEST_TOP_K, topK);
    }
    if (choiceCount != null) {
      span.setAttribute(GEN_AI_REQUEST_CHOICE_COUNT, choiceCount);
    }
    if (outputType != null) {
      span.setAttribute(GEN_AI_OUTPUT_TYPE, outputType);
    }
    if (stream != null) {
      span.setAttribute(GEN_AI_REQUEST_STREAM, stream);
    }
    if (conversationId != null) {
      span.setAttribute(GEN_AI_CONVERSATION_ID, conversationId);
    }

    AttributesBuilder contentBuilder = Attributes.builder();
    GenAiContentAttributes.appendInferenceContent(
        contentBuilder, inputMessages, outputMessages, systemInstruction, toolDefinitions, true);
    span.setAllAttributes(contentBuilder.build());

    // Response attributes
    if (responseModel != null) {
      span.setAttribute(GEN_AI_RESPONSE_MODEL, responseModel);
    }
    if (responseId != null) {
      span.setAttribute(GEN_AI_RESPONSE_ID, responseId);
    }
    List<String> resolvedFinishReasons = GenAiFinishReasons.resolve(finishReasons, outputMessages);
    if (resolvedFinishReasons != null) {
      span.setAttribute(GEN_AI_RESPONSE_FINISH_REASONS, resolvedFinishReasons);
    }
    if (inputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, inputTokens);
    }
    Long combinedOutputTokens = combinedOutputTokens();
    if (combinedOutputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_OUTPUT_TOKENS, combinedOutputTokens);
    }
    if (thinkingTokens != null) {
      span.setAttribute(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, thinkingTokens);
    }
    if (cacheCreationInputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_CACHE_CREATION_INPUT_TOKENS, cacheCreationInputTokens);
    }
    if (cacheReadInputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, cacheReadInputTokens);
    }
    if (timeToFirstChunk != null) {
      span.setAttribute(GEN_AI_RESPONSE_TIME_TO_FIRST_CHUNK, timeToFirstChunk);
    }
  }

  @Override
  protected Attributes buildMetricAttributes() {
    return SpanAttributeHelper.commonMetricAttributes(
            operationName(),
            provider,
            requestModel,
            responseModel,
            serverAddress,
            serverPort,
            errorType)
        .build();
  }

  @Override
  protected long getInputTokens() {
    return inputTokens != null ? inputTokens : -1;
  }

  @Override
  protected long getOutputTokens() {
    if (outputTokens == null) {
      return -1;
    }
    return outputTokens;
  }

  /**
   * Builds the full attribute set for an inference event, mirroring Python {@code _get_attributes()
   * + _get_message_attributes(for_span=False)}.
   */
  Attributes buildEventAttributes(@Nullable String errorType, Map<String, String> extraAttributes) {
    AttributesBuilder builder = Attributes.builder();
    builder.put(GEN_AI_OPERATION_NAME, operationName());
    builder.put(GEN_AI_PROVIDER_NAME, provider);
    if (requestModel != null) {
      builder.put(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (serverAddress != null) {
      builder.put(SERVER_ADDRESS, serverAddress);
    }
    if (serverPort != null) {
      builder.put(SERVER_PORT, serverPort.longValue());
    }
    if (temperature != null) {
      builder.put(GEN_AI_REQUEST_TEMPERATURE, temperature);
    }
    if (topP != null) {
      builder.put(GEN_AI_REQUEST_TOP_P, topP);
    }
    if (frequencyPenalty != null) {
      builder.put(GEN_AI_REQUEST_FREQUENCY_PENALTY, frequencyPenalty);
    }
    if (presencePenalty != null) {
      builder.put(GEN_AI_REQUEST_PRESENCE_PENALTY, presencePenalty);
    }
    if (maxTokens != null) {
      builder.put(GEN_AI_REQUEST_MAX_TOKENS, maxTokens);
    }
    if (stopSequences != null) {
      builder.put(GEN_AI_REQUEST_STOP_SEQUENCES, stopSequences);
    }
    if (seed != null) {
      builder.put(GEN_AI_REQUEST_SEED, seed);
    }
    if (topK != null) {
      builder.put(GEN_AI_REQUEST_TOP_K, topK);
    }
    if (choiceCount != null) {
      builder.put(GEN_AI_REQUEST_CHOICE_COUNT, choiceCount);
    }
    if (outputType != null) {
      builder.put(GEN_AI_OUTPUT_TYPE, outputType);
    }
    if (stream != null) {
      builder.put(GEN_AI_REQUEST_STREAM, stream);
    }
    if (conversationId != null) {
      builder.put(GEN_AI_CONVERSATION_ID, conversationId);
    }
    if (responseModel != null) {
      builder.put(GEN_AI_RESPONSE_MODEL, responseModel);
    }
    if (responseId != null) {
      builder.put(GEN_AI_RESPONSE_ID, responseId);
    }
    List<String> resolvedFinishReasons = GenAiFinishReasons.resolve(finishReasons, outputMessages);
    if (resolvedFinishReasons != null) {
      builder.put(GEN_AI_RESPONSE_FINISH_REASONS, resolvedFinishReasons);
    }
    if (inputTokens != null) {
      builder.put(GEN_AI_USAGE_INPUT_TOKENS, inputTokens);
    }
    Long combinedOutput = combinedOutputTokens();
    if (combinedOutput != null) {
      builder.put(GEN_AI_USAGE_OUTPUT_TOKENS, combinedOutput);
    }
    if (thinkingTokens != null) {
      builder.put(GEN_AI_USAGE_REASONING_OUTPUT_TOKENS, thinkingTokens);
    }
    if (cacheCreationInputTokens != null) {
      builder.put(GEN_AI_USAGE_CACHE_CREATION_INPUT_TOKENS, cacheCreationInputTokens);
    }
    if (cacheReadInputTokens != null) {
      builder.put(GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS, cacheReadInputTokens);
    }
    if (timeToFirstChunk != null) {
      builder.put(GEN_AI_RESPONSE_TIME_TO_FIRST_CHUNK, timeToFirstChunk);
    }
    GenAiContentAttributes.appendInferenceContent(
        builder, inputMessages, outputMessages, systemInstruction, toolDefinitions, false);
    extraAttributes.forEach(builder::put);
    if (errorType != null) {
      builder.put(ErrorIncubatingAttributes.ERROR_TYPE, errorType);
    }
    return builder.build();
  }

  @Nullable
  private Long combinedOutputTokens() {
    if (outputTokens == null && thinkingTokens == null) {
      return null;
    }
    long output = outputTokens != null ? outputTokens : 0;
    long thinking = thinkingTokens != null ? thinkingTokens : 0;
    return output + thinking;
  }
}
