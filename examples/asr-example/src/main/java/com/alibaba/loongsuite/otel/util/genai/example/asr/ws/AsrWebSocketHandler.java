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

package com.alibaba.loongsuite.otel.util.genai.example.asr.ws;

import com.alibaba.loongsuite.otel.util.genai.example.asr.service.AsrTranscriptionService;
import com.alibaba.loongsuite.otel.util.genai.example.asr.service.VoiceTurnService;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

/**
 * WebSocket handler for the voice assistant pipeline; GenAI semantic convention instrumentation
 * is delegated to {@link VoiceTurnService}.
 */
@Component
public class AsrWebSocketHandler extends BinaryWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(AsrWebSocketHandler.class);

  private final AsrTranscriptionService asrService;
  private final VoiceTurnService voiceTurnService;
  private final Map<String, AtomicBoolean> processingFlags = new ConcurrentHashMap<>();

  public AsrWebSocketHandler(
      AsrTranscriptionService asrService, VoiceTurnService voiceTurnService) {
    this.asrService = asrService;
    this.voiceTurnService = voiceTurnService;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = session.getId();
    log.info("WebSocket connected: {}", sessionId);
    processingFlags.put(sessionId, new AtomicBoolean(false));
    asrService.startStream(sessionId);
    session.sendMessage(
        new TextMessage("{\"type\":\"connected\",\"sessionId\":\"" + sessionId + "\"}"));
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    ByteBuffer payload = message.getPayload();
    byte[] audio = new byte[payload.remaining()];
    payload.get(audio);
    asrService.appendAudio(session.getId(), audio);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    if ("END".equals(message.getPayload())) {
      processAudioComplete(session);
    }
  }

  private void processAudioComplete(WebSocketSession session) {
    String sessionId = session.getId();
    AtomicBoolean processing = processingFlags.get(sessionId);
    if (processing == null || !processing.compareAndSet(false, true)) {
      return;
    }
    Thread worker =
        new Thread(
            () -> {
              try {
                voiceTurnService.processTurn(session);
              } catch (Exception e) {
                log.error("Voice turn failed for session {}", sessionId, e);
                try {
                  session.sendMessage(
                      new TextMessage(
                          "{\"type\":\"error\",\"message\":\""
                              + e.getMessage().replace("\"", "'")
                              + "\"}"));
                } catch (Exception ex) {
                  log.error("Failed to send error", ex);
                }
              } finally {
                processing.set(false);
              }
            },
            "voice-turn-" + sessionId);
    worker.start();
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String sessionId = session.getId();
    log.info("WebSocket closed: {} ({})", sessionId, status);
    asrService.cleanup(sessionId);
    processingFlags.remove(sessionId);
  }
}
