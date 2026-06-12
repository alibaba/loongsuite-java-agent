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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenAiEventTest {

  private InMemorySpanExporter spanExporter;
  private InMemoryLogRecordExporter logExporter;
  private GenAiTelemetryHandler handler;

  @BeforeEach
  void setUp() {
    spanExporter = InMemorySpanExporter.create();
    logExporter = InMemoryLogRecordExporter.create();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build())
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                    .build())
            .build();
    handler = GenAiTelemetryHandler.create(sdk);
  }

  @Test
  void exceptionEventIncludesStacktraceWhenFailWithThrowable() {
    RuntimeException error = new RuntimeException("boom");
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.fail(error);
    }

    LogRecordData event = findEvent("gen_ai.client.operation.exception");
    assertEquals(
        RuntimeException.class.getSimpleName(),
        event.getAttributes().get(AttributeKey.stringKey("exception.type")));
    assertEquals("boom", event.getAttributes().get(AttributeKey.stringKey("exception.message")));
    String stacktrace = event.getAttributes().get(AttributeKey.stringKey("exception.stacktrace"));
    assertNotNull(stacktrace);
    assertTrue(stacktrace.contains("RuntimeException: boom"));
    assertTrue(stacktrace.contains("GenAiEventTest"));
  }

  @Test
  void exceptionEventOmitsStacktraceWhenFailWithStrings() {
    try (InferenceInvocation inv = handler.inference("openai", "gpt-4o")) {
      inv.fail("timeout", "request timed out");
    }

    LogRecordData event = findEvent("gen_ai.client.operation.exception");
    assertEquals("timeout", event.getAttributes().get(AttributeKey.stringKey("exception.type")));
    assertNull(event.getAttributes().get(AttributeKey.stringKey("exception.stacktrace")));
  }

  @Test
  void evaluationEventIncludesErrorTypeOnFailure() {
    handler.emitEvaluationResult("helpfulness", null, null, null, "chatcmpl-1", "timeout");

    LogRecordData event = findEvent("gen_ai.evaluation.result");
    assertEquals(
        "helpfulness", event.getAttributes().get(AttributeKey.stringKey("gen_ai.evaluation.name")));
    assertEquals("timeout", event.getAttributes().get(AttributeKey.stringKey("error.type")));
    assertEquals(
        "chatcmpl-1", event.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));
    assertNull(
        event.getAttributes().get(AttributeKey.doubleKey("gen_ai.evaluation.score.value")));
  }

  @Test
  void evaluationEventOmitsErrorTypeOnSuccess() {
    handler.emitEvaluationResult("helpfulness", 0.9, "pass", "looks good", "chatcmpl-2");

    LogRecordData event = findEvent("gen_ai.evaluation.result");
    assertNull(event.getAttributes().get(AttributeKey.stringKey("error.type")));
    assertEquals(0.9, event.getAttributes().get(AttributeKey.doubleKey("gen_ai.evaluation.score.value")));
  }

  private LogRecordData findEvent(String eventName) {
    return logExporter.getFinishedLogRecordItems().stream()
        .filter(record -> eventName.equals(record.getEventName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing event: " + eventName));
  }
}
