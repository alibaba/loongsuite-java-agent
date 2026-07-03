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

package com.alibaba.loongsuite.otel.util.genai.example.asr.service;

import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.alibaba.loongsuite.otel.util.genai.InferenceInvocation;
import com.alibaba.loongsuite.otel.util.genai.WorkflowInvocation;
import com.alibaba.loongsuite.otel.util.genai.example.common.CallbackStreamMetrics;
import com.alibaba.loongsuite.otel.util.genai.example.common.GenAiOperations;
import com.alibaba.loongsuite.otel.util.genai.example.common.VoiceSessionTelemetry;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import io.opentelemetry.api.trace.Tracer;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Span orchestration for one voice turn (read in order: this class → LlmService → Asr/Tts → WeatherToolService).
 *
 * <p>Span hierarchy:
 * <pre>
 * websocket.session (VoiceSessionTelemetry, INTERNAL)
 * └─ invoke_workflow voice_assistant_turn
 *    ├─ generate_content {asr-model}   ← ASR, input=BlobPart(PCM); upload optional (see application.yml)
 *    ├─ chat {llm-model}               ← intent + reply (LlmService)
 *    ├─ execute_tool get_weather       ← optional (WeatherToolService)
 *    └─ generate_content {tts-model}   ← TTS, output=BlobPart(MP3); upload optional
 * </pre>
 */
@Service
public class VoiceTurnService {

  private static final Logger log = LoggerFactory.getLogger(VoiceTurnService.class);

  private final Tracer tracer;
  private final GenAiTelemetryHandler genAi;
  private final AsrTranscriptionService asrService;
  private final LlmService llmService;
  private final TtsSynthesisService ttsService;
  private final WeatherToolService weatherToolService;
  private final String provider;
  private final String asrModel;
  private final String ttsModel;
  private final String serverHost;
  private final Integer serverPort;

  public VoiceTurnService(
      Tracer tracer,
      GenAiTelemetryHandler genAi,
      AsrTranscriptionService asrService,
      LlmService llmService,
      TtsSynthesisService ttsService,
      WeatherToolService weatherToolService,
      @Value("${genai.provider}") String provider,
      @Value("${dashscope.asr.model}") String asrModel,
      @Value("${dashscope.tts.model}") String ttsModel,
      @Value("${dashscope.server-host}") String serverHost,
      Integer genAiServerPort) {
    this.tracer = tracer;
    this.genAi = genAi;
    this.asrService = asrService;
    this.llmService = llmService;
    this.ttsService = ttsService;
    this.weatherToolService = weatherToolService;
    this.provider = provider;
    this.asrModel = asrModel;
    this.ttsModel = ttsModel;
    this.serverHost = serverHost;
    this.serverPort = genAiServerPort;
  }

  public void processTurn(WebSocketSession session) throws Exception {
    String sessionId = session.getId();
    String conversationId = sessionId;
    String wsUrl = session.getUri() != null ? session.getUri().toString() : "ws://localhost/ws/asr";

    // --- Layer 1: WebSocket session span (not a GenAI operation; conversation.id / url only) ---
    try (VoiceSessionTelemetry voiceSession =
        new VoiceSessionTelemetry(tracer, conversationId, wsUrl)) {
      // --- Layer 2: whole voice turn workflow span ---
      try (WorkflowInvocation turn = genAi.workflow("voice_assistant_turn")) {
        turn.setAttribute("gen_ai.conversation.id", conversationId);

        String transcript;
        // --- Layer 3a: ASR inference span (operation.name=generate_content) ---
        // Create InferenceInvocation here and pass to AsrTranscriptionService so endStream can fill messages
        try (InferenceInvocation asrInvocation =
            genAi.inference(
                provider,
                asrModel,
                serverHost,
                serverPort,
                GenAiOperations.GENERATE_CONTENT)) {
          asrInvocation.setConversationId(conversationId);
          CallbackStreamMetrics asrMetrics = new CallbackStreamMetrics(asrInvocation);
          transcript = asrService.endStream(sessionId, asrInvocation, asrMetrics);
        }

        if (transcript == null || transcript.trim().isEmpty()) {
          session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"未能识别语音内容\"}"));
          return;
        }

        session.sendMessage(
            new TextMessage(
                "{\"type\":\"transcript\",\"text\":\"" + escapeJson(transcript) + "\"}"));

        // --- Layer 3b/3c: LLM chat spans (intent + reply, see LlmService) ---
        String intent = llmService.classifyIntent(conversationId, transcript);
        session.sendMessage(new TextMessage("{\"type\":\"intent\",\"value\":\"" + intent + "\"}"));

        String reply;
        if ("weather".equals(intent)) {
          String cityKey = extractCity(transcript);
          // --- Layer 3d: execute_tool span (sibling of LLM spans, both under workflow) ---
          String weatherSummary = weatherToolService.getWeather(cityKey);
          reply =
              llmService.generateReply(
                  conversationId,
                  "用户问：" + transcript + "\n查到的天气：" + weatherSummary + "\n请用口语简短回答。");
        } else {
          reply = llmService.generateReply(conversationId, transcript);
        }

        session.sendMessage(
            new TextMessage("{\"type\":\"text\",\"text\":\"" + escapeJson(reply) + "\"}"));

        // --- Layer 3e: TTS inference span (output=BlobPart; external upload is optional via multimodal.* config) ---
        try (InferenceInvocation ttsInvocation =
            genAi.inference(
                provider,
                ttsModel,
                serverHost,
                serverPort,
                GenAiOperations.GENERATE_CONTENT)) {
          ttsInvocation.setConversationId(conversationId);
          CallbackStreamMetrics ttsMetrics = new CallbackStreamMetrics(ttsInvocation);
          ttsService.synthesize(
              ttsInvocation,
              ttsMetrics,
              reply,
              chunk -> {
                try {
                  if (session.isOpen()) {
                    session.sendMessage(new BinaryMessage(chunk));
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
        }

        // Workflow-level input/output: user transcript + assistant final text reply
        turn.setInputMessages(
            Collections.singletonList(
                new InputMessage("user", Collections.singletonList(new TextPart(transcript)))));
        turn.setOutputMessages(
            Collections.singletonList(
                new OutputMessage(
                    "assistant", Collections.singletonList(new TextPart(reply)), "stop")));

        session.sendMessage(new TextMessage("{\"type\":\"complete\"}"));
      }
    }
  }

  private static String escapeJson(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private static String extractCity(String text) {
    if (text.contains("北京")) {
      return "beijing";
    }
    if (text.contains("上海")) {
      return "shanghai";
    }
    if (text.contains("杭州")) {
      return "hangzhou";
    }
    return "hangzhou";
  }
}
