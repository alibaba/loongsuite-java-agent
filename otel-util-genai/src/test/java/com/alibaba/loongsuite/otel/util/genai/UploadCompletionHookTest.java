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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadCompletionHookTest {

  @TempDir Path tempDir;

  @Test
  void tryCreateWithoutBasePathReturnsNoOp() {
    CompletionHook hook = UploadCompletionHook.tryCreate();
    assertInstanceOf(NoOpCompletionHook.class, hook);
  }

  @Test
  void uploadStampsRefsOnSpanAndEvent() throws Exception {
    System.setProperty("otel.instrumentation.genai.upload.base.path", tempDir.toString());

    try {
      CompletionHook hook = UploadCompletionHook.tryCreate();
      assertInstanceOf(UploadCompletionHook.class, hook);

      InMemorySpanExporter exporter = InMemorySpanExporter.create();
      SdkTracerProvider tracerProvider =
          SdkTracerProvider.builder()
              .addSpanProcessor(SimpleSpanProcessor.create(exporter))
              .build();
      OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
      GenAiTelemetryHandler handler =
          GenAiTelemetryHandler.builder(sdk).setCompletionHook(hook).build();

      System.setProperty("otel.semconv.stability.opt.in", "gen_ai_latest_experimental");
      System.setProperty("otel.instrumentation.genai.capture.message.content", "event_only");
      System.setProperty("otel.instrumentation.genai.emit.event", "true");

      try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
        inv.setInputMessages(
            Collections.singletonList(
                new InputMessage("user", Collections.singletonList(new TextPart("hi")))));
        inv.setOutputMessages(
            Collections.singletonList(
                new OutputMessage(
                    "assistant", Collections.singletonList(new TextPart("hello")), "stop")));
      }

      List<io.opentelemetry.sdk.trace.data.SpanData> spans = exporter.getFinishedSpanItems();
      assertEquals(1, spans.size());
      io.opentelemetry.api.common.Attributes attrs = spans.get(0).getAttributes();
      assertNotNull(attrs.get(AttributeKey.stringKey("gen_ai.input.messages_ref")));
      assertNotNull(attrs.get(AttributeKey.stringKey("gen_ai.output.messages_ref")));

      if (hook instanceof UploadCompletionHook) {
        ((UploadCompletionHook) hook).shutdown();
      }
      Thread.sleep(200);
      assertTrue(Files.list(tempDir).anyMatch(p -> p.getFileName().toString().contains("inputs")));
    } finally {
      System.clearProperty("otel.instrumentation.genai.upload.base.path");
      System.clearProperty("otel.semconv.stability.opt.in");
      System.clearProperty("otel.instrumentation.genai.capture.message.content");
      System.clearProperty("otel.instrumentation.genai.emit.event");
    }
  }
}
