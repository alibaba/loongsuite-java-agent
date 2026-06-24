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

package com.alibaba.loongsuite.otel.util.genai.example;

import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.alibaba.loongsuite.otel.util.genai.InferenceInvocation;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

  private final GenAiTelemetryHandler handler;
  private final OpenAIClient openAIClient;
  private final String provider;
  private final String model;
  private final double temperature;
  private final long maxTokens;
  private final String serverAddress;
  private final Integer serverPort;

  public ChatService(
      GenAiTelemetryHandler handler,
      OpenAIClient openAIClient,
      @Value("${genai.provider}") String provider,
      @Value("${genai.model}") String model,
      @Value("${genai.temperature}") double temperature,
      @Value("${genai.max-tokens}") long maxTokens,
      String genAiServerAddress,
      Integer genAiServerPort) {
    this.handler = handler;
    this.openAIClient = openAIClient;
    this.provider = provider;
    this.model = model;
    this.temperature = temperature;
    this.maxTokens = maxTokens;
    this.serverAddress = genAiServerAddress;
    this.serverPort = genAiServerPort;
  }

  public ChatResponse chat(String userMessage) {
    try (InferenceInvocation inv =
        handler.inference(provider, model, serverAddress, serverPort, null)) {
      setRequestAttributes(inv, userMessage);

      ChatCompletion completion = callLlm(userMessage);

      ChatResponse response = extractResponse(completion);
      setResponseAttributes(inv, completion, response);

      return response;
    }
  }

  private void setRequestAttributes(InferenceInvocation inv, String userMessage) {
    inv.setInputMessages(
        Collections.singletonList(
            new InputMessage("user", Collections.singletonList(new TextPart(userMessage)))));
    inv.setTemperature(temperature);
    inv.setMaxTokens(maxTokens);
  }

  private ChatCompletion callLlm(String userMessage) {
    ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .model(model)
            .maxCompletionTokens(maxTokens)
            .addUserMessage(userMessage)
            .build();
    return openAIClient.chat().completions().create(params);
  }

  private void setResponseAttributes(
      InferenceInvocation inv, ChatCompletion completion, ChatResponse response) {
    List<String> finishReasons =
        completion.choices().stream()
            .map(choice -> choice.finishReason().toString())
            .collect(Collectors.toList());

    inv.setOutputMessages(
        Collections.singletonList(
            new OutputMessage(
                "assistant",
                Collections.singletonList(new TextPart(response.getContent())),
                finishReasons.get(0))));
    inv.setResponseModel(completion.model());
    inv.setResponseId(completion.id());
    inv.setFinishReasons(finishReasons);
    inv.setInputTokens(response.getInputTokens());
    inv.setOutputTokens(response.getOutputTokens());
  }

  private ChatResponse extractResponse(ChatCompletion completion) {
    String content =
        completion.choices().stream()
            .flatMap(choice -> choice.message().content().stream())
            .collect(Collectors.joining());

    return new ChatResponse(
        content,
        completion.model(),
        completion.id(),
        completion.usage().map(u -> u.promptTokens()).orElse(0L),
        completion.usage().map(u -> u.completionTokens()).orElse(0L));
  }

  public static class ChatResponse {
    private final String content;
    private final String model;
    private final String id;
    private final long inputTokens;
    private final long outputTokens;

    public ChatResponse(String content, String model, String id, long inputTokens, long outputTokens) {
      this.content = content;
      this.model = model;
      this.id = id;
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
    }

    public String getContent() { return content; }
    public String getModel() { return model; }
    public String getId() { return id; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
  }
}
