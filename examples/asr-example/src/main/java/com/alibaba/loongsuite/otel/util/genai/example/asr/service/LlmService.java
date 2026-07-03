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
import com.alibaba.loongsuite.otel.util.genai.example.common.GenAiOperations;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** LLM Chat instrumentation: pass {@link GenAiOperations#CHAT} explicitly as operation.name. */
@Service
public class LlmService {

  private static final Logger log = LoggerFactory.getLogger(LlmService.class);

  private final GenAiTelemetryHandler handler;
  private final OpenAIClient openAIClient;
  private final String provider;
  private final String model;
  private final String serverAddress;
  private final Integer serverPort;

  public LlmService(
      GenAiTelemetryHandler handler,
      OpenAIClient openAIClient,
      @Value("${genai.provider}") String provider,
      @Value("${genai.model}") String model,
      String genAiServerAddress,
      Integer genAiServerPort) {
    this.handler = handler;
    this.openAIClient = openAIClient;
    this.provider = provider;
    this.model = model;
    this.serverAddress = genAiServerAddress;
    this.serverPort = genAiServerPort;
  }

  /** Chat completion for intent classification — {@code gen_ai.operation.name = chat}. */
  public String classifyIntent(String conversationId, String text) {
    String prompt =
        "判断用户意图，只回复一个英文单词：\n"
            + "- weather：询问天气（如「今天杭州天气怎么样」）\n"
            + "- chitchat：其他闲聊\n\n"
            + "用户输入："
            + text;

    // handler.inference(..., GenAiOperations.CHAT) → span: chat {model}
    try (InferenceInvocation inv =
        handler.inference(provider, model, serverAddress, serverPort, GenAiOperations.CHAT)) {
      inv.setConversationId(conversationId);
      inv.setInputMessages(
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart(prompt)))));

      ChatCompletionCreateParams params =
          ChatCompletionCreateParams.builder()
              .model(model)
              .addUserMessage(prompt)
              .maxCompletionTokens(20L)
              .build();
      ChatCompletion completion = openAIClient.chat().completions().create(params);

      String content =
          completion.choices().stream()
              .map(choice -> choice.message().content().orElse(""))
              .collect(Collectors.joining())
              .trim();

      // Response side: setOutputMessages + usage + response id/model
      inv.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant",
                  Collections.singletonList(new TextPart(content)),
                  completion.choices().isEmpty()
                      ? "stop"
                      : completion.choices().get(0).finishReason().toString())));
      inv.setResponseId(completion.id());
      inv.setResponseModel(completion.model());
      completion
          .usage()
          .ifPresent(
              u -> {
                inv.setInputTokens(u.promptTokens());
                inv.setOutputTokens(u.completionTokens());
              });

      String intent = normalizeIntent(content, text);
      log.info("Intent for '{}': {} (raw={})", text, intent, content);
      return intent;
    }
  }

  private static String normalizeIntent(String modelOutput, String userText) {
    String lower = modelOutput == null ? "" : modelOutput.toLowerCase();
    if (lower.contains("weather") || userText.contains("天气")) {
      return "weather";
    }
    return "chitchat";
  }

  /** Chat completion for reply generation — {@code gen_ai.operation.name = chat}. */
  public String generateReply(String conversationId, String userPrompt) {
    try (InferenceInvocation inv =
        handler.inference(provider, model, serverAddress, serverPort, GenAiOperations.CHAT)) {
      inv.setConversationId(conversationId);
      // Same pattern as classifyIntent: input messages → call LLM → output messages + usage
      inv.setInputMessages(
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart(userPrompt)))));

      ChatCompletionCreateParams params =
          ChatCompletionCreateParams.builder().model(model).addUserMessage(userPrompt).build();
      ChatCompletion completion = openAIClient.chat().completions().create(params);

      String content =
          completion.choices().stream()
              .map(choice -> choice.message().content().orElse(""))
              .collect(Collectors.joining());

      List<String> finishReasons =
          completion.choices().stream()
              .map(c -> c.finishReason().toString())
              .collect(Collectors.toList());

      inv.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant",
                  Collections.singletonList(new TextPart(content)),
                  finishReasons.isEmpty() ? "stop" : finishReasons.get(0))));
      inv.setResponseId(completion.id());
      inv.setResponseModel(completion.model());
      completion
          .usage()
          .ifPresent(
              u -> {
                inv.setInputTokens(u.promptTokens());
                inv.setOutputTokens(u.completionTokens());
              });

      return content;
    }
  }
}
