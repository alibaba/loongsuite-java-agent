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

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.loongsuite.otel.util.genai.InferenceInvocation;
import com.alibaba.loongsuite.otel.util.genai.example.common.CallbackStreamMetrics;
import com.alibaba.loongsuite.otel.util.genai.types.BlobPart;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** TTS business logic; span is created in {@link VoiceTurnService} and passed to {@link #synthesize}. */
@Service
public class TtsSynthesisService {

  private static final Logger log = LoggerFactory.getLogger(TtsSynthesisService.class);
  private static final String WAV_MIME = "audio/wav";

  @Value("${genai.api-key}")
  private String apiKey;

  @Value("${dashscope.tts.model}")
  private String model;

  @Value("${dashscope.tts.voice}")
  private String voice;

  public void synthesize(
      InferenceInvocation invocation,
      CallbackStreamMetrics metrics,
      String text,
      Consumer<byte[]> onAudioChunk) {
    if (text == null || text.trim().isEmpty()) {
      return;
    }
    if (invocation != null) {
      invocation.setStream(true);
      invocation.setOutputType("speech"); // → gen_ai.output.type=speech
      invocation.setInputMessages(
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart(text)))));
    }

    ByteArrayOutputStream audioOut = new ByteArrayOutputStream();
    AtomicReference<Exception> errorRef = new AtomicReference<>();

    try {
      SpeechSynthesisParam param =
          SpeechSynthesisParam.builder()
              .model(model)
              .voice(voice)
              .apiKey(apiKey)
              .format(SpeechSynthesisAudioFormat.WAV_22050HZ_MONO_16BIT)
              .build();

      SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
      synthesizer
          .callAsFlowable(text)
          .blockingSubscribe(
              result -> {
                if (result != null && result.getAudioFrame() != null) {
                  if (metrics != null) {
                    metrics.onChunk();
                  }
                  ByteBuffer frame = result.getAudioFrame();
                  int remaining = frame.remaining();
                  if (remaining > 0) {
                    byte[] audio = new byte[remaining];
                    frame.get(audio);
                    try {
                      audioOut.write(audio);
                    } catch (java.io.IOException e) {
                      throw new RuntimeException("Failed to buffer TTS audio", e);
                    }
                    onAudioChunk.accept(audio);
                  }
                }
              },
              error -> {
                log.error("TTS synthesis failed", error);
                errorRef.set(error instanceof Exception ? (Exception) error : new Exception(error));
              },
              () -> log.debug("TTS synthesis completed"));
    } catch (Exception e) {
      log.error("TTS call failed", e);
      errorRef.set(e);
    }

    if (invocation != null) {
      Exception error = errorRef.get();
      if (error != null) {
        invocation.fail(error);
        return;
      }
      byte[] wav = audioOut.toByteArray();
      if (wav.length == 0) {
        invocation.fail("EmptySpeech", "TTS returned no audio");
        return;
      }
      invocation.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant",
                  Collections.singletonList(new BlobPart(WAV_MIME, wav)),
                  "stop")));
    }
  }
}
