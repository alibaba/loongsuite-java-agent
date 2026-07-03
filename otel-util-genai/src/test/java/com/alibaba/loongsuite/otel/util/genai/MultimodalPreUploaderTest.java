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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.types.BlobPart;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import com.alibaba.loongsuite.otel.util.genai.types.UriPart;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultimodalPreUploaderTest {

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    System.setProperty("otel.semconv.stability.opt.in", "gen_ai_latest_experimental");
    System.setProperty("otel.instrumentation.genai.multimodal.upload.mode", "both");
    System.setProperty(
        "otel.instrumentation.genai.multimodal.storage.base.path", tempDir.toString());
  }

  @AfterEach
  void tearDown() {
    System.clearProperty("otel.semconv.stability.opt.in");
    System.clearProperty("otel.instrumentation.genai.multimodal.upload.mode");
    System.clearProperty("otel.instrumentation.genai.multimodal.storage.base.path");
    System.clearProperty("otel.instrumentation.genai.multimodal.audio.conversion");
    System.clearProperty("otel.instrumentation.genai.capture.message.content");
  }

  @Test
  void replacesBlobWithUri() {
    byte[] pcm = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
    List<InputMessage> inputs =
        Collections.singletonList(
            new InputMessage(
                "user",
                Collections.singletonList(new BlobPart("audio", "audio/pcm", pcm))));

    MultimodalPreUploader preUploader = new MultimodalPreUploader(tempDir.toString());
    SpanContext spanContext =
        SpanContext.create(
            "00000000000000000000000000000001",
            "0000000000000001",
            TraceFlags.getSampled(),
            TraceState.getDefault());

    long startTimeEpochMillis = System.currentTimeMillis();
    MultimodalPreUploader.ProcessResult result =
        preUploader.process(spanContext, startTimeEpochMillis, inputs, Collections.emptyList());

    assertEquals(1, result.uploadItems().size());
    MessagePart part = result.inputMessages().get(0).parts().get(0);
    assertInstanceOf(UriPart.class, part);
    UriPart uriPart = (UriPart) part;
    assertTrue(uriPart.uri().contains(tempDir.toString()) || uriPart.uri().startsWith("file://"));
    assertEquals("audio/wav", uriPart.mimeType());
    assertEquals("audio", uriPart.modality());
    assertTrue(uriPart.uri().endsWith(".wav"));

    String expectedDate =
        DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(startTimeEpochMillis));
    assertTrue(uriPart.uri().contains("/" + expectedDate + "/"));
    assertEquals(
        String.valueOf(startTimeEpochMillis / 1000L),
        result.uploadItems().get(0).meta().get("timestamp"));
  }

  @Test
  void multimodalHookStampsMetadataOnSpan() throws Exception {
    System.setProperty("otel.instrumentation.genai.capture.message.content", "span_only");

    byte[] mp3 = new byte[] {0x49, 0x44, 0x33, 1, 2, 3};
    MultimodalCompletionHook hook = MultimodalCompletionHook.tryCreate();
    org.junit.jupiter.api.Assertions.assertNotNull(hook);

    io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter exporter =
        io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter.create();
    io.opentelemetry.sdk.trace.SdkTracerProvider tracerProvider =
        io.opentelemetry.sdk.trace.SdkTracerProvider.builder()
            .addSpanProcessor(
                io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(exporter))
            .build();
    io.opentelemetry.sdk.OpenTelemetrySdk sdk =
        io.opentelemetry.sdk.OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    GenAiTelemetryHandler handler = GenAiTelemetryHandler.builder(sdk).build();

    try (InferenceInvocation inv = handler.inference("dashscope", "fun-asr-realtime", null, null, "generate_content")) {
      inv.setInputMessages(
          Collections.singletonList(
              new InputMessage(
                  "user",
                  Collections.singletonList(new BlobPart("audio", "audio/pcm", mp3)))));
      inv.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant",
                  Collections.singletonList(new TextPart("hello")),
                  "stop")));
      inv.stop();
    }

    Thread.sleep(500);
    String metadata =
        exporter.getFinishedSpanItems().get(0).getAttributes().get(
            AttributeKey.stringKey("gen_ai.input.multimodal_metadata"));
    assertTrue(metadata.contains("\"type\":\"uri\""));
    assertTrue(metadata.contains("\"modality\":\"audio\""));

    String messages =
        exporter.getFinishedSpanItems().get(0).getAttributes().get(
            AttributeKey.stringKey("gen_ai.input.messages"));
    assertTrue(messages.contains("\"type\":\"uri\""));
    org.junit.jupiter.api.Assertions.assertFalse(messages.contains("blob"));

    hook.shutdown();
  }

  @Test
  void localUploaderWritesFile() throws Exception {
    LocalFileUploader uploader = LocalFileUploader.tryCreate(tempDir.toString());
    org.junit.jupiter.api.Assertions.assertNotNull(uploader);

    byte[] data = "audio-bytes".getBytes();
    MultimodalUploadItem item =
        new MultimodalUploadItem(
            tempDir.resolve("20250702/test.bin").toString(),
            "audio/pcm",
            data,
            Collections.singletonMap("traceId", "abc"));

    assertTrue(uploader.upload(item));
    uploader.shutdown(2_000);

    for (int i = 0; i < 20; i++) {
      Path target = tempDir.resolve("20250702/test.bin");
      if (Files.exists(target)) {
        assertEquals(data.length, Files.size(target));
        return;
      }
      TimeUnit.MILLISECONDS.sleep(100);
    }
    org.junit.jupiter.api.Assertions.fail("upload file not created");
  }

  @Test
  void chainedHookLoadsMultimodalAndUpload() {
    System.setProperty("otel.instrumentation.genai.completion.hook", "upload");
    System.setProperty("otel.instrumentation.genai.upload.base.path", tempDir.toString());
    System.setProperty("otel.instrumentation.genai.capture.message.content", "span_only");

    try {
      CompletionHook hook = CompletionHookLoader.load();
      assertInstanceOf(ChainedCompletionHook.class, hook);
    } finally {
      System.clearProperty("otel.instrumentation.genai.completion.hook");
      System.clearProperty("otel.instrumentation.genai.upload.base.path");
    }
  }
}
