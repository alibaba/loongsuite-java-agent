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

import com.alibaba.loongsuite.otel.util.genai.CreateAgentInvocation;
import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;

import java.util.Collections;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CreateAgentService {

  private final GenAiTelemetryHandler handler;
  private final String provider;
  private final String model;

  public CreateAgentService(
      GenAiTelemetryHandler handler,
      @Value("${genai.provider}") String provider,
      @Value("${genai.model}") String model) {
    this.handler = handler;
    this.provider = provider;
    this.model = model;
  }

  public CreateAgentResponse create(String agentName, String description, String instructions) {
    try (CreateAgentInvocation inv = handler.createAgent(provider, model, agentName)) {
      String agentId = "agent-" + UUID.randomUUID().toString().substring(0, 8);
      inv.setAgentId(agentId);
      inv.setAgentDescription(description);
      inv.setAgentVersion("1.0.0");
      inv.setSystemInstruction(Collections.singletonList(new TextPart(instructions)));

      return new CreateAgentResponse(agentId, agentName, description, model);
    }
  }

  public static class CreateAgentResponse {
    private final String agentId;
    private final String name;
    private final String description;
    private final String model;

    public CreateAgentResponse(String agentId, String name, String description, String model) {
      this.agentId = agentId;
      this.name = name;
      this.description = description;
      this.model = model;
    }

    public String getAgentId() {
      return agentId;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public String getModel() {
      return model;
    }
  }
}
