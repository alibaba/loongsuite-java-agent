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

import com.alibaba.loongsuite.otel.util.genai.EmbeddingInvocation;
import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

  private final GenAiTelemetryHandler handler;
  private final OpenAIClient openAIClient;
  private final String provider;
  private final String serverAddress;
  private final Integer serverPort;

  public EmbeddingService(
      GenAiTelemetryHandler handler,
      OpenAIClient openAIClient,
      @Value("${genai.provider}") String provider,
      String genAiServerAddress,
      Integer genAiServerPort) {
    this.handler = handler;
    this.openAIClient = openAIClient;
    this.provider = provider;
    this.serverAddress = genAiServerAddress;
    this.serverPort = genAiServerPort;
  }

  public EmbeddingResponse embed(String input, String model) {
    try (EmbeddingInvocation inv = handler.embedding(provider, model, serverAddress, serverPort)) {
      EmbeddingCreateParams params =
          EmbeddingCreateParams.builder().model(model).input(input).build();

      CreateEmbeddingResponse response = openAIClient.embeddings().create(params);

      int dimensions = response.data().get(0).embedding().size();
      inv.setResponseModel(response.model());
      inv.setInputTokens(response.usage().promptTokens());
      inv.setDimensionCount((long) dimensions);

      return new EmbeddingResponse(
          response.model(), dimensions, response.usage().promptTokens(), response.data().size());
    }
  }

  public static class EmbeddingResponse {
    private final String model;
    private final int dimensions;
    private final long inputTokens;
    private final int vectorCount;

    public EmbeddingResponse(String model, int dimensions, long inputTokens, int vectorCount) {
      this.model = model;
      this.dimensions = dimensions;
      this.inputTokens = inputTokens;
      this.vectorCount = vectorCount;
    }

    public String getModel() {
      return model;
    }

    public int getDimensions() {
      return dimensions;
    }

    public long getInputTokens() {
      return inputTokens;
    }

    public int getVectorCount() {
      return vectorCount;
    }
  }
}
