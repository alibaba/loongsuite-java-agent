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

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.loongsuite.otel.util.genai.InferenceInvocation;
import com.alibaba.loongsuite.otel.util.genai.example.common.CallbackStreamMetrics;
import com.alibaba.loongsuite.otel.util.genai.types.BlobPart;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** ASR business logic; caller creates InferenceInvocation and passes it to {@link #endStream}. */
@Service
public class AsrTranscriptionService {

  private static final Logger log = LoggerFactory.getLogger(AsrTranscriptionService.class);
  private static final String PCM_MIME = "audio/pcm";

  @Value("${genai.api-key}")
  private String apiKey;

  @Value("${dashscope.asr.model}")
  private String model;

  @Value("${dashscope.asr.sample-rate}")
  private int sampleRate;

  private final Map<String, Recognition> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> transcripts = new ConcurrentHashMap<>();
  private final Map<String, CountDownLatch> completionLatches = new ConcurrentHashMap<>();
  private final Map<String, ByteArrayOutputStream> audioBuffers = new ConcurrentHashMap<>();

  public void startStream(String sessionId) {
    try {
      Recognition recognition = new Recognition();
      CountDownLatch latch = new CountDownLatch(1);
      sessions.put(sessionId, recognition);
      transcripts.put(sessionId, "");
      completionLatches.put(sessionId, latch);
      audioBuffers.put(sessionId, new ByteArrayOutputStream());

      RecognitionParam param =
          RecognitionParam.builder()
              .model(model)
              .apiKey(apiKey)
              .sampleRate(sampleRate)
              .format("pcm")
              .build();

      recognition.call(
          param,
          new ResultCallback<RecognitionResult>() {
            @Override
            public void onEvent(RecognitionResult result) {
              if (result != null && result.getSentence() != null) {
                String text = result.getSentence().getText();
                if (text != null && !text.isEmpty()) {
                  transcripts.put(sessionId, text);
                }
              }
            }

            @Override
            public void onComplete() {
              latch.countDown();
            }

            @Override
            public void onError(Exception e) {
              log.error("ASR error for session {}", sessionId, e);
              latch.countDown();
            }
          });
    } catch (Exception e) {
      throw new RuntimeException("ASR initialization failed", e);
    }
  }

  public void appendAudio(String sessionId, byte[] audioBytes) {
    Recognition recognition = sessions.get(sessionId);
    if (recognition == null) {
      startStream(sessionId);
      recognition = sessions.get(sessionId);
    }
    ByteArrayOutputStream buffer = audioBuffers.get(sessionId);
    if (buffer != null && audioBytes != null && audioBytes.length > 0) {
      try {
        buffer.write(audioBytes);
      } catch (java.io.IOException e) {
        throw new RuntimeException("Failed to buffer ASR audio", e);
      }
    }
    if (recognition != null) {
      recognition.sendAudioFrame(ByteBuffer.wrap(audioBytes));
    }
  }

  /**
   * Ends the ASR stream and fills the inference span: input=PCM BlobPart, output=recognized text.
   * BlobPart is always valid; external upload is optional (see {@code otel.instrumentation.genai.multimodal.*}).
   * {@code metrics} records streaming stats via {@link CallbackStreamMetrics#onChunk()} in DashScope callbacks.
   */
  public String endStream(
      String sessionId, InferenceInvocation invocation, CallbackStreamMetrics metrics) {
    Recognition recognition = sessions.get(sessionId);
    CountDownLatch latch = completionLatches.get(sessionId);
    if (recognition == null || latch == null) {
      return "";
    }
    byte[] pcm = audioBytes(sessionId);
    if (invocation != null) {
      invocation.setStream(true);
      invocation.setOutputType("text");
      if (pcm.length > 0) {
        // BlobPart in messages works without multimodal config; when multimodal.* is enabled,
        // MultimodalCompletionHook uploads and replaces BlobPart with UriPart before span end
        invocation.setInputMessages(
            Collections.singletonList(
                new InputMessage(
                    "user",
                    Collections.singletonList(new BlobPart(PCM_MIME, pcm)))));
      }
    }
    try {
      Thread.sleep(300);
      recognition.stop();
      latch.await(30, TimeUnit.SECONDS);
      String text = transcripts.getOrDefault(sessionId, "");
      if (invocation != null) {
        if (text != null && !text.isEmpty()) {
          invocation.setOutputMessages(
              Collections.singletonList(
                  new OutputMessage(
                      "assistant",
                      Collections.singletonList(new TextPart(text)),
                      "stop")));
          invocation.setResponseId(sessionId);
        } else {
          // Business failure: invocation.fail() marks span ERROR and sets error.type
          invocation.fail("EmptyTranscript", "未能识别语音内容");
        }
      }
      return text;
    } catch (Exception e) {
      log.error("Failed to end ASR for session {}", sessionId, e);
      if (invocation != null) {
        invocation.fail(e);
      }
      return transcripts.getOrDefault(sessionId, "");
    } finally {
      sessions.remove(sessionId);
      transcripts.remove(sessionId);
      completionLatches.remove(sessionId);
      audioBuffers.remove(sessionId);
    }
  }

  public void cleanup(String sessionId) {
    sessions.remove(sessionId);
    transcripts.remove(sessionId);
    completionLatches.remove(sessionId);
    audioBuffers.remove(sessionId);
  }

  private byte[] audioBytes(String sessionId) {
    ByteArrayOutputStream buffer = audioBuffers.get(sessionId);
    return buffer != null ? buffer.toByteArray() : new byte[0];
  }
}
