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

package com.alibaba.loongsuite.otel.util.genai.example.basic;

import com.alibaba.loongsuite.otel.util.genai.AgentInvocation;
import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

  private final GenAiTelemetryHandler handler;
  private final ChatService chatService;
  private final String provider;
  private final String model;

  public AgentService(
      GenAiTelemetryHandler handler,
      ChatService chatService,
      @Value("${genai.provider}") String provider,
      @Value("${genai.model}") String model) {
    this.handler = handler;
    this.chatService = chatService;
    this.provider = provider;
    this.model = model;
  }

  public AgentResponse invokeAgent(String agentName, String task) {
    try (AgentInvocation inv = handler.invokeLocalAgent(provider, model, agentName)) {
      inv.setAgentId("agent-" + agentName);
      inv.setAgentDescription("Example agent: " + agentName);
      inv.setInputMessages(
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart(task)))));

      ChatService.ChatResponse llmResponse = chatService.chat(task);

      inv.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant",
                  Collections.singletonList(new TextPart(llmResponse.getContent())),
                  "stop")));
      inv.setInputTokens(llmResponse.getInputTokens());
      inv.setOutputTokens(llmResponse.getOutputTokens());

      return new AgentResponse(
          agentName,
          llmResponse.getContent(),
          llmResponse.getInputTokens(),
          llmResponse.getOutputTokens());
    }
  }

  public static class AgentResponse {
    private final String agentName;
    private final String content;
    private final long inputTokens;
    private final long outputTokens;

    public AgentResponse(String agentName, String content, long inputTokens, long outputTokens) {
      this.agentName = agentName;
      this.content = content;
      this.inputTokens = inputTokens;
      this.outputTokens = outputTokens;
    }

    public String getAgentName() {
      return agentName;
    }

    public String getContent() {
      return content;
    }

    public long getInputTokens() {
      return inputTokens;
    }

    public long getOutputTokens() {
      return outputTokens;
    }
  }
}
