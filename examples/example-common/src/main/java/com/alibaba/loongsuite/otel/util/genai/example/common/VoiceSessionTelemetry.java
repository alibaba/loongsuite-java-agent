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

package com.alibaba.loongsuite.otel.util.genai.example.common;

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_CONVERSATION_ID;
import static io.opentelemetry.semconv.incubating.NetworkIncubatingAttributes.NETWORK_TRANSPORT;
import static io.opentelemetry.semconv.incubating.UrlIncubatingAttributes.URL_FULL;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Session-level INTERNAL span (WebSocket connection lifetime), not a GenAI operation.
 * GenAI spans should nest under this (see nested try-with-resources in {@code VoiceTurnService}).
 */
public final class VoiceSessionTelemetry implements AutoCloseable {

  private final Span sessionSpan;
  private final Scope scope;

  public VoiceSessionTelemetry(Tracer tracer, String conversationId, String wsUrl) {
    sessionSpan =
        tracer
            .spanBuilder("websocket.session")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(NETWORK_TRANSPORT, "websocket")
            .setAttribute(URL_FULL, wsUrl)
            .setAttribute(GEN_AI_CONVERSATION_ID, conversationId)
            .startSpan();
    scope = sessionSpan.makeCurrent();
  }

  public void fail(Throwable e) {
    sessionSpan.recordException(e);
    sessionSpan.setStatus(StatusCode.ERROR);
  }

  @Override
  public void close() {
    scope.close();
    sessionSpan.end();
  }
}
