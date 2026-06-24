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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.stream.GenAiStreamWrapper;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemainingGapsTest {

  private InMemorySpanExporter spanExporter;
  private GenAiTelemetryHandler handler;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build())
            .build();
    handler = GenAiTelemetryHandler.create(sdk);
  }

  @Test
  void toolInvocationUsesInternalSpanKind() {
    try (ToolInvocation inv = handler.tool("search")) {
      // no-op
    }
    assertEquals(SpanKind.INTERNAL, spanExporter.getFinishedSpanItems().get(0).getKind());
  }

  @Test
  void finishReasonsDerivedFromOutputMessages() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.setOutputMessages(
          Arrays.asList(
              new OutputMessage("assistant", Collections.singletonList(new TextPart("hi")), "stop"),
              new OutputMessage(
                  "assistant", Collections.singletonList(new TextPart("bye")), "length")));
    }
    Attributes attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertEquals(
        Arrays.asList("stop", "length"),
        attrs.get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons")));
  }

  @Test
  void agentCacheTokensOnSpan() {
    try (AgentInvocation inv = handler.invokeLocalAgent("openai", "gpt-4o", "agent")) {
      inv.setOutputTokens(80L);
      inv.setCacheCreationInputTokens(5L);
      inv.setCacheReadInputTokens(10L);
    }
    Attributes attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertEquals(80L, attrs.get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
    assertEquals(null, attrs.get(AttributeKey.longKey("gen_ai.usage.reasoning.output_tokens")));
    assertEquals(5L, attrs.get(AttributeKey.longKey("gen_ai.usage.cache_creation.input_tokens")));
    assertEquals(10L, attrs.get(AttributeKey.longKey("gen_ai.usage.cache_read.input_tokens")));
  }

  @Test
  void remoteAgentRunCapturesErrors() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                handler.remoteAgentRun(
                    "openai",
                    "gpt-4o",
                    "remote-agent",
                    "agent.example.com",
                    443,
                    inv -> {
                      throw new RuntimeException("boom");
                    }));
    assertEquals("boom", thrown.getMessage());
    SpanData span = spanExporter.getFinishedSpanItems().get(0);
    assertEquals(
        RuntimeException.class.getSimpleName(),
        span.getAttributes().get(AttributeKey.stringKey("error.type")));
  }

  @Test
  void streamWrapperRecordsTimingOnInvocation() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.setStream(true);
      Iterator<String> iterator = Arrays.asList("a", "b", "c").iterator();
      GenAiStreamWrapper<String> wrapper =
          new GenAiStreamWrapper<String>(iterator, inv) {
            @Override
            protected void processChunk(String chunk) {}

            @Override
            protected void onStreamEnd() {}

            @Override
            protected void onStreamError(Throwable error) {}
          };
      List<String> collected = new ArrayList<>();
      while (wrapper.hasNext()) {
        collected.add(wrapper.next());
      }
      assertEquals(3, collected.size());
      assertEquals(2, inv.getInterChunkDelays().size());
      assertTrue(inv.getTimeToFirstChunk() != null && inv.getTimeToFirstChunk() >= 0);
    }

    Attributes attrs = spanExporter.getFinishedSpanItems().get(0).getAttributes();
    assertTrue(attrs.get(AttributeKey.doubleKey("gen_ai.response.time_to_first_chunk")) != null);
    assertEquals(true, attrs.get(AttributeKey.booleanKey("gen_ai.request.stream")));
  }
}
