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

import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.EVENT_ONLY;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.SPAN_AND_EVENT;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.SPAN_ONLY;

import com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import java.util.List;

/**
 * Shared logic for GenAI message content attributes on spans and events.
 *
 * <p>Mirrors Python {@code get_content_attributes(for_span=...)} behavior:
 * <ul>
 *   <li>{@code forSpan=true}: content is serialized as a JSON string wrapped in {@link Value}
 *   <li>{@code forSpan=false}: content is serialized as structured {@link Value} (list of maps)
 * </ul>
 */
final class GenAiContentAttributes {

  private GenAiContentAttributes() {}

  static void appendInferenceContent(
      AttributesBuilder builder,
      List<InputMessage> inputMessages,
      List<OutputMessage> outputMessages,
      List<MessagePart> systemInstruction,
      List<ToolDefinition> toolDefinitions,
      boolean forSpan) {
    if (!GenAiConfigUtil.isExperimentalMode()) {
      return;
    }

    ContentCapturingMode mode = GenAiConfigUtil.getContentCapturingMode();
    boolean allowed =
        forSpan
            ? mode == SPAN_ONLY || mode == SPAN_AND_EVENT
            : mode == EVENT_ONLY || mode == SPAN_AND_EVENT;

    if (!allowed) {
      if (!toolDefinitions.isEmpty()) {
        builder.put(
            GenAiAttributes.GEN_AI_TOOL_DEFINITIONS,
            forSpan ? toJsonValue(toolDefinitions) : GenAiContentSerializer.toValue(toolDefinitions));
      }
      return;
    }

    if (!inputMessages.isEmpty()) {
      builder.put(
          GenAiAttributes.GEN_AI_INPUT_MESSAGES,
          forSpan ? toJsonValue(inputMessages) : GenAiContentSerializer.toValue(inputMessages));
    }
    if (!outputMessages.isEmpty()) {
      builder.put(
          GenAiAttributes.GEN_AI_OUTPUT_MESSAGES,
          forSpan ? toJsonValue(outputMessages) : GenAiContentSerializer.toValue(outputMessages));
    }
    if (!systemInstruction.isEmpty()) {
      builder.put(
          GenAiAttributes.GEN_AI_SYSTEM_INSTRUCTIONS,
          forSpan
              ? toJsonValue(systemInstruction)
              : GenAiContentSerializer.toValue(systemInstruction));
    }
    if (!toolDefinitions.isEmpty()) {
      builder.put(
          GenAiAttributes.GEN_AI_TOOL_DEFINITIONS,
          forSpan ? toJsonValue(toolDefinitions) : GenAiContentSerializer.toValue(toolDefinitions));
    }
  }

  static void appendWorkflowContent(
      AttributesBuilder builder,
      List<InputMessage> inputMessages,
      List<OutputMessage> outputMessages) {
    if (!GenAiConfigUtil.isExperimentalMode() || !GenAiConfigUtil.shouldCaptureContentOnSpans()) {
      return;
    }
    if (!inputMessages.isEmpty()) {
      builder.put(GenAiAttributes.GEN_AI_INPUT_MESSAGES, toJsonValue(inputMessages));
    }
    if (!outputMessages.isEmpty()) {
      builder.put(GenAiAttributes.GEN_AI_OUTPUT_MESSAGES, toJsonValue(outputMessages));
    }
  }

  private static Value<?> toJsonValue(List<?> items) {
    return Value.of(GenAiContentSerializer.toJsonString(items));
  }
}
