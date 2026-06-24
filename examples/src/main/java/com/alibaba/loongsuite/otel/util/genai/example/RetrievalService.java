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
import com.alibaba.loongsuite.otel.util.genai.RetrievalInvocation;
import com.alibaba.loongsuite.otel.util.genai.types.RetrievalDocument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

  private final GenAiTelemetryHandler handler;
  private final String provider;
  private final String serverAddress;
  private final Integer serverPort;

  public RetrievalService(
      GenAiTelemetryHandler handler,
      @Value("${genai.provider}") String provider,
      String genAiServerAddress,
      Integer genAiServerPort) {
    this.handler = handler;
    this.provider = provider;
    this.serverAddress = genAiServerAddress;
    this.serverPort = genAiServerPort;
  }

  public RetrievalResponse retrieve(String query, String dataSourceId, int topK) {
    try (RetrievalInvocation inv =
        handler.retrieval(provider, dataSourceId, serverAddress, serverPort)) {
      inv.setQueryText(query);
      inv.setTopK((double) topK);

      List<DocumentResult> docs = mockSearch(query, topK);
      inv.setDocuments(
          docs.stream()
              .map(d -> new RetrievalDocument(d.getId(), d.getScore(), d.getSnippet()))
              .collect(Collectors.toList()));

      return new RetrievalResponse(dataSourceId, query, docs);
    }
  }

  private List<DocumentResult> mockSearch(String query, int topK) {
    return Arrays.asList(
        new DocumentResult("doc-001", 0.95, "OpenTelemetry is an observability framework..."),
        new DocumentResult("doc-002", 0.87, "Semantic conventions define attribute names..."),
        new DocumentResult("doc-003", 0.72, "GenAI spans track LLM operations..."));
  }

  public static class DocumentResult {
    private final String id;
    private final double score;
    private final String snippet;

    public DocumentResult(String id, double score, String snippet) {
      this.id = id;
      this.score = score;
      this.snippet = snippet;
    }

    public String getId() { return id; }
    public double getScore() { return score; }
    public String getSnippet() { return snippet; }
  }

  public static class RetrievalResponse {
    private final String dataSourceId;
    private final String query;
    private final List<DocumentResult> documents;

    public RetrievalResponse(String dataSourceId, String query, List<DocumentResult> documents) {
      this.dataSourceId = dataSourceId;
      this.query = query;
      this.documents = documents;
    }

    public String getDataSourceId() { return dataSourceId; }
    public String getQuery() { return query; }
    public List<DocumentResult> getDocuments() { return documents; }
  }
}
