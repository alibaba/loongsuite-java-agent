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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenAiTelemetryHandlerTest {

  private InMemorySpanExporter spanExporter;
  private OpenTelemetry openTelemetry;
  private GenAiTelemetryHandler handler;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
    openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    handler = GenAiTelemetryHandler.builder(openTelemetry).setCompletionHook(context -> {}).build();
  }

  @AfterEach
  void tearDown() {
    spanExporter.reset();
  }

  @Test
  void testInferenceBasic() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.setTemperature(0.7);
      inv.setInputMessages(
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart("Hello")))));
      inv.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant", Collections.singletonList(new TextPart("Hi there!")), "stop")));
      inv.setResponseModel("gpt-4o-2024-08-06");
      inv.setInputTokens(10L);
      inv.setOutputTokens(5L);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("chat gpt-4o", span.getName());
    assertEquals(SpanKind.CLIENT, span.getKind());

    // Common attributes
    assertEquals("chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "openai", span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")));
    assertEquals(
        "gpt-4o", span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));

    // Response attributes
    assertEquals(
        "gpt-4o-2024-08-06",
        span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")));
    assertEquals(10L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    assertEquals(5L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));

    // Request attributes
    assertEquals(
        0.7, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.temperature")));
  }

  @Test
  void testInferenceWithError() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.fail(new RuntimeException("test error"));
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
    assertEquals(
        "RuntimeException", span.getAttributes().get(AttributeKey.stringKey("error.type")));
  }

  @Test
  void testInferenceWithExplicitError() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.fail("rate_limit_exceeded", "Rate limit exceeded");
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
    assertEquals(
        "rate_limit_exceeded", span.getAttributes().get(AttributeKey.stringKey("error.type")));
  }

  @Test
  void testInferenceAutoCloseOnException() {
    try {
      try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
        inv.setInputTokens(42L);
        throw new IllegalStateException("simulated error");
      }
    } catch (IllegalStateException expected) {
      // expected
    }

    // Span should still be created and ended (not leaked)
    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertNotNull(span);
    assertEquals("chat gpt-4o", span.getName());
    // close() calls stop() since fail() was never called, so status is UNSET (success)
    assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
  }

  @Test
  void testInferenceStopExplicitly() {
    InferenceInvocation inv = handler.inference("openai", "gpt-4o");
    inv.setInputTokens(100L);
    inv.setOutputTokens(50L);
    inv.stop();

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());
    assertEquals("chat gpt-4o", spans.get(0).getName());
  }

  @Test
  void testInferenceWithAllRequestAttributes() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.setTemperature(0.9);
      inv.setTopP(0.95);
      inv.setFrequencyPenalty(0.5);
      inv.setPresencePenalty(0.3);
      inv.setMaxTokens(1024L);
      inv.setStopSequences(Arrays.asList("END", "STOP"));
      inv.setSeed(42L);
      inv.setFinishReasons(Collections.singletonList("stop"));
      inv.setResponseId("chatcmpl-abc123");
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals(
        0.9, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.temperature")));
    assertEquals(0.95, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.top_p")));
    assertEquals(
        0.5, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.frequency_penalty")));
    assertEquals(
        0.3, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.presence_penalty")));
    assertEquals(
        1024L, span.getAttributes().get(AttributeKey.longKey("gen_ai.request.max_tokens")));
    assertEquals(42L, span.getAttributes().get(AttributeKey.longKey("gen_ai.request.seed")));
    assertEquals(
        Arrays.asList("END", "STOP"),
        span.getAttributes().get(AttributeKey.stringArrayKey("gen_ai.request.stop_sequences")));
    assertEquals(
        Collections.singletonList("stop"),
        span.getAttributes().get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons")));
    assertEquals(
        "chatcmpl-abc123", span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));
  }

  @Test
  void testInferenceCustomOperationName() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o", null, null, "generate")) {
      inv.setInputTokens(10L);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("generate gpt-4o", span.getName());
    assertEquals(
        "generate", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
  }

  @Test
  void testEmbeddingInvocation() {
    try (EmbeddingInvocation inv = handler.embedding("openai", "text-embedding-3-small")) {
      inv.setEncodingFormats(Arrays.asList("float", "base64"));
      inv.setInputTokens(25L);
      inv.setDimensionCount(1536L);
      inv.setResponseModel("text-embedding-3-small");
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("embeddings text-embedding-3-small", span.getName());
    assertEquals(SpanKind.CLIENT, span.getKind());

    assertEquals(
        "embeddings", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "openai", span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")));
    assertEquals(
        "text-embedding-3-small",
        span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
    assertEquals(25L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    assertEquals(
        1536L, span.getAttributes().get(AttributeKey.longKey("gen_ai.embeddings.dimension.count")));
    assertEquals(
        Arrays.asList("float", "base64"),
        span.getAttributes().get(AttributeKey.stringArrayKey("gen_ai.request.encoding_formats")));
  }

  @Test
  void testToolInvocation() {
    try (ToolInvocation inv = handler.tool("get_weather", "call_123", "function", "Get weather")) {
      inv.setArguments("{\"city\": \"Beijing\"}");
      inv.setToolResult("{\"temperature\": 25}");
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("execute_tool get_weather", span.getName());
    assertEquals(SpanKind.INTERNAL, span.getKind());
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")) == null);

    assertEquals(
        "execute_tool", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "get_weather", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
    assertEquals(
        "call_123", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.call.id")));
    assertEquals("function", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.type")));
    assertEquals(
        "Get weather", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.description")));
  }

  @Test
  void testToolInvocationMinimal() {
    try (ToolInvocation inv = handler.tool("search")) {
      // no extra attributes
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("execute_tool search", span.getName());
    assertEquals(SpanKind.INTERNAL, span.getKind());
    assertEquals("search", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
  }

  @Test
  void testWorkflowInvocation() {
    try (WorkflowInvocation inv = handler.workflow("my-pipeline")) {
      inv.setInputMessages(
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart("process this")))));
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("invoke_workflow my-pipeline", span.getName());
    assertEquals(SpanKind.INTERNAL, span.getKind());

    assertEquals(
        "invoke_workflow",
        span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "my-pipeline", span.getAttributes().get(AttributeKey.stringKey("gen_ai.workflow.name")));
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")) == null);
  }

  @Test
  void testWorkflowInvocationNullName() {
    try (WorkflowInvocation inv = handler.workflow(null)) {
      // no name
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());
    assertEquals("invoke_workflow", spans.get(0).getName());
  }

  @Test
  void testLocalAgentInvocation() {
    try (AgentInvocation inv = handler.invokeLocalAgent("openai", "gpt-4o", "research-agent")) {
      inv.setAgentId("agent-001");
      inv.setAgentDescription("A research assistant");
      inv.setInputTokens(200L);
      inv.setOutputTokens(150L);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("invoke_agent research-agent", span.getName());
    assertEquals(SpanKind.INTERNAL, span.getKind());

    assertEquals(
        "invoke_agent", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "research-agent", span.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.name")));
    assertEquals("agent-001", span.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.id")));
    assertEquals(
        "A research assistant",
        span.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.description")));
    assertEquals(
        "openai", span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")));
    assertEquals(
        "gpt-4o", span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("server.address")) == null);
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")) == null);
  }

  @Test
  void testRemoteAgentInvocation() {
    try (AgentInvocation inv =
        handler.invokeRemoteAgent("openai", "gpt-4o", "code-agent", "agent.example.com", 443)) {
      inv.setAgentVersion("1.0.0");
      inv.setConversationId("conv-xyz");
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("invoke_agent code-agent", span.getName());
    assertEquals(SpanKind.CLIENT, span.getKind());

    assertEquals(
        "invoke_agent", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "code-agent", span.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.name")));
    assertEquals(
        "agent.example.com", span.getAttributes().get(AttributeKey.stringKey("server.address")));
    assertEquals(443L, span.getAttributes().get(AttributeKey.longKey("server.port")));
    assertEquals("1.0.0", span.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.version")));
    assertEquals(
        "conv-xyz", span.getAttributes().get(AttributeKey.stringKey("gen_ai.conversation.id")));
  }

  @Test
  void testCustomAttribute() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.setAttribute("custom.key", "custom-value");
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());
    assertEquals(
        "custom-value", spans.get(0).getAttributes().get(AttributeKey.stringKey("custom.key")));
  }

  @Test
  void testDoubleCloseIsIdempotent() {
    InferenceInvocation inv = handler.inference("openai", "gpt-4o");
    inv.close();
    inv.close(); // second close should be a no-op

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());
  }

  @Test
  void testFailThenCloseDoesNotOverwrite() {
    InferenceInvocation inv = handler.inference("openai", "gpt-4o");
    inv.fail(new RuntimeException("first error"));
    inv.close(); // should be a no-op since fail() already finished it

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());
    assertEquals(StatusCode.ERROR, spans.get(0).getStatus().getStatusCode());
  }

  @Test
  void testCreateHandlerWithStaticFactory() {
    // Verify that GenAiTelemetryHandler.create(openTelemetry) works
    GenAiTelemetryHandler defaultHandler = GenAiTelemetryHandler.create(openTelemetry);
    assertNotNull(defaultHandler);

    try (InferenceInvocation inv = defaultHandler.inference("test-provider", "test-model")) {
      inv.setInputTokens(1L);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertTrue(spans.size() >= 1);
  }

  @Test
  void testInferenceWithServerAddress() {
    try (InferenceInvocation inv =
        handler.inference("openai", "gpt-4o", "api.openai.com", 443, null)) {
      inv.setInputTokens(10L);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals(
        "api.openai.com", span.getAttributes().get(AttributeKey.stringKey("server.address")));
    assertEquals(443L, span.getAttributes().get(AttributeKey.longKey("server.port")));
    // operationName defaults to "chat" when null
    assertEquals("chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
  }

  @Test
  void testEmbeddingWithError() {
    try (EmbeddingInvocation inv = handler.embedding("openai", "text-embedding-3-small")) {
      inv.fail(new IllegalStateException("connection timeout"));
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
    assertEquals(
        "IllegalStateException", span.getAttributes().get(AttributeKey.stringKey("error.type")));
  }

  @Test
  void testInferenceNullModel() {
    try (InferenceInvocation inv = handler.inference("openai", null)) {
      inv.setInputTokens(5L);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());
    // When model is null, span name is just the operation name
    assertEquals("chat", spans.get(0).getName());
  }

  @Test
  void testRetrievalWithServerAddress() {
    try (RetrievalInvocation inv =
        handler.retrieval("pinecone", "kb-product-docs", "api.pinecone.io", 443)) {
      inv.setRequestModel("text-embedding-3-small");
      inv.setTopK(5.0);
    }

    List<SpanData> spans = spanExporter.getFinishedSpanItems();
    assertEquals(1, spans.size());

    SpanData span = spans.get(0);
    assertEquals("retrieval kb-product-docs", span.getName());
    assertEquals(SpanKind.CLIENT, span.getKind());
    assertEquals(
        "retrieval", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals(
        "pinecone", span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")));
    assertEquals(
        "kb-product-docs",
        span.getAttributes().get(AttributeKey.stringKey("gen_ai.data_source.id")));
    assertEquals(
        "text-embedding-3-small",
        span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
    assertEquals(
        "api.pinecone.io", span.getAttributes().get(AttributeKey.stringKey("server.address")));
    assertEquals(443L, span.getAttributes().get(AttributeKey.longKey("server.port")));
    assertEquals(5.0, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.top_k")));
  }
}
