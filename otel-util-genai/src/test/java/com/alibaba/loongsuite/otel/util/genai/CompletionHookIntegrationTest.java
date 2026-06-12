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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompletionHookIntegrationTest {

  private final List<String> propsToClear = new ArrayList<>();

  @AfterEach
  void clearProps() {
    for (String prop : propsToClear) {
      System.clearProperty(prop);
    }
    propsToClear.clear();
  }

  private void setProp(String key, String value) {
    System.setProperty(key, value);
    propsToClear.add(key);
  }

  @Test
  void shouldCaptureContentWhenHookConfigured() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build();

    GenAiTelemetryHandler handler =
        GenAiTelemetryHandler.builder(sdk)
            .setCompletionHook(
                context -> {
                  // no-op but real hook
                })
            .build();

    assertTrue(handler.shouldCaptureContent());
  }

  @Test
  void inferenceHookReceivesLogRecordBeforeEmit() {
    AtomicReference<EventLogRecord> capturedRecord = new AtomicReference<>();

    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build();

    setProp("otel.semconv.stability.opt.in", "gen_ai_latest_experimental");
    setProp("otel.instrumentation.genai.capture.message.content", "event_only");
    setProp("otel.instrumentation.genai.emit.event", "true");

    GenAiTelemetryHandler handler =
        GenAiTelemetryHandler.builder(sdk)
            .setCompletionHook(
                context -> {
                  capturedRecord.set(context.getLogRecord());
                  if (context.getLogRecord() != null) {
                    context
                        .getLogRecord()
                        .setAttribute("custom.ref", "s3://bucket/object.json");
                  }
                })
            .build();

    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.setInputMessages(List.of(new InputMessage("user", List.of(new TextPart("hi")))));
      inv.setInputTokens(10L);
      inv.setOutputTokens(5L);
      inv.setResponseModel("gpt-4o-2024");
    }

    assertNotNull(capturedRecord.get());
    assertEquals(
        10L,
        capturedRecord
            .get()
            .getAttributes()
            .get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    assertEquals(
        "s3://bucket/object.json",
        capturedRecord.get().getAttributes().get(AttributeKey.stringKey("custom.ref")));
  }

  @Test
  void agentInvocationDoesNotProvideLogRecord() {
    AtomicReference<EventLogRecord> capturedRecord = new AtomicReference<>();

    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build();

    setProp("otel.semconv.stability.opt.in", "gen_ai_latest_experimental");
    setProp("otel.instrumentation.genai.emit.event", "true");

    GenAiTelemetryHandler handler =
        GenAiTelemetryHandler.builder(sdk)
            .setCompletionHook(context -> capturedRecord.set(context.getLogRecord()))
            .build();

    try (AgentInvocation inv = handler.invokeLocalAgent("openai", "gpt-4o", "my-agent")) {
      inv.setInputMessages(List.of(new InputMessage("user", List.of(new TextPart("hi")))));
    }

    assertNull(capturedRecord.get());
  }

  @Test
  void workflowInvocationInvokesCompletionHook() {
    AtomicReference<List<InputMessage>> capturedInputs = new AtomicReference<>();

    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                    .build())
            .build();

    GenAiTelemetryHandler handler =
        GenAiTelemetryHandler.builder(sdk)
            .setCompletionHook(context -> capturedInputs.set(context.getInputs()))
            .build();

    List<InputMessage> inputs = List.of(new InputMessage("user", List.of(new TextPart("step1"))));
    try (WorkflowInvocation inv = handler.workflow("pipeline")) {
      inv.setInputMessages(inputs);
    }

    assertEquals(inputs, capturedInputs.get());
    assertFalse(capturedInputs.get().isEmpty());
  }
}
