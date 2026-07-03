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

/**
 * LoongSuite extended attribute: logical GenAI span kind values for {@code gen_ai.span.kind}.
 *
 * <p>This is distinct from OTel's native {@code SpanKind} (CLIENT/INTERNAL/etc.). It represents the
 * logical role of a GenAI operation within an AI application pipeline.
 *
 * <p>Gated by {@link GenAiConfigUtil#isExtendedEnabled()}.
 */
public final class GenAiSpanKindValues {

  public static final String LLM = "LLM";
  public static final String AGENT = "AGENT";
  public static final String TOOL = "TOOL";
  public static final String EMBEDDING = "EMBEDDING";
  public static final String RETRIEVER = "RETRIEVER";
  public static final String RERANKER = "RERANKER";
  public static final String MEMORY = "MEMORY";
  public static final String ENTRY = "ENTRY";
  public static final String STEP = "STEP";
  public static final String WORKFLOW = "WORKFLOW";

  private GenAiSpanKindValues() {}
}
