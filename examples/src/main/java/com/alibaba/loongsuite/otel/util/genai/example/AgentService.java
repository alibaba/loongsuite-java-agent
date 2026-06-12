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

import com.alibaba.loongsuite.otel.util.genai.AgentInvocation;
import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import java.util.List;
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
          List.of(new InputMessage("user", List.of(new TextPart(task)))));

      ChatService.ChatResponse llmResponse = chatService.chat(task);

      inv.setOutputMessages(
          List.of(
              new OutputMessage(
                  "assistant",
                  List.of(new TextPart(llmResponse.content())),
                  "stop")));
      inv.setInputTokens(llmResponse.inputTokens());
      inv.setOutputTokens(llmResponse.outputTokens());

      return new AgentResponse(agentName, llmResponse.content(),
          llmResponse.inputTokens(), llmResponse.outputTokens());
    }
  }

  public record AgentResponse(
      String agentName, String content, long inputTokens, long outputTokens) {}
}
