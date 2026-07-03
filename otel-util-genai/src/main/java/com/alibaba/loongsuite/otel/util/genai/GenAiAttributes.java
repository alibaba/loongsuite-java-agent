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

import static io.opentelemetry.api.common.AttributeKey.valueKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;

/**
 * GenAI attribute keys with {@code type: any} from the semconv registry.
 *
 * <p>The Java semconv codegen ({@code GenAiIncubatingAttributes}) does not generate constants for
 * {@code any}-typed attributes. Python exposes them as string constants in {@code
 * gen_ai_attributes}; here we use {@link AttributeKey#valueKey(String)} with {@link Value} to
 * support structured content on spans and events per semconv.
 */
final class GenAiAttributes {

  static final AttributeKey<Value<?>> GEN_AI_INPUT_MESSAGES = valueKey("gen_ai.input.messages");

  static final AttributeKey<Value<?>> GEN_AI_OUTPUT_MESSAGES = valueKey("gen_ai.output.messages");

  static final AttributeKey<Value<?>> GEN_AI_SYSTEM_INSTRUCTIONS =
      valueKey("gen_ai.system_instructions");

  static final AttributeKey<Value<?>> GEN_AI_TOOL_DEFINITIONS = valueKey("gen_ai.tool.definitions");

  static final AttributeKey<Value<?>> GEN_AI_TOOL_CALL_ARGUMENTS =
      valueKey("gen_ai.tool.call.arguments");

  static final AttributeKey<Value<?>> GEN_AI_TOOL_CALL_RESULT = valueKey("gen_ai.tool.call.result");

  static final AttributeKey<Value<?>> GEN_AI_RETRIEVAL_DOCUMENTS =
      valueKey("gen_ai.retrieval.documents");

  static final AttributeKey<String> GEN_AI_INPUT_MESSAGES_REF =
      AttributeKey.stringKey("gen_ai.input.messages_ref");

  static final AttributeKey<String> GEN_AI_OUTPUT_MESSAGES_REF =
      AttributeKey.stringKey("gen_ai.output.messages_ref");

  static final AttributeKey<String> GEN_AI_SYSTEM_INSTRUCTIONS_REF =
      AttributeKey.stringKey("gen_ai.system_instructions_ref");

  static final AttributeKey<String> GEN_AI_TOOL_DEFINITIONS_REF =
      AttributeKey.stringKey("gen_ai.tool.definitions_ref");

  /** LoongSuite extended attribute (not in OTel GenAI semconv 1.41.1). */
  static final AttributeKey<String> GEN_AI_INPUT_MULTIMODAL_METADATA =
      AttributeKey.stringKey("gen_ai.input.multimodal_metadata");

  /** LoongSuite extended attribute (not in OTel GenAI semconv 1.41.1). */
  static final AttributeKey<String> GEN_AI_OUTPUT_MULTIMODAL_METADATA =
      AttributeKey.stringKey("gen_ai.output.multimodal_metadata");

  /**
   * LoongSuite extended attribute: logical GenAI span kind.
   *
   * <p>Represents the role of the operation in the AI pipeline (LLM, AGENT, TOOL, etc.).
   * Distinct from OTel's SpanKind (CLIENT/INTERNAL). Gated by {@code isExtendedEnabled()}.
   *
   * @see GenAiSpanKindValues
   */
  static final AttributeKey<String> GEN_AI_SPAN_KIND =
      AttributeKey.stringKey("gen_ai.span.kind");

  private GenAiAttributes() {}
}
