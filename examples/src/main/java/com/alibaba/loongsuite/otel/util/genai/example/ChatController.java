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

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

  private final ChatService chatService;
  private final EmbeddingService embeddingService;
  private final ToolService toolService;
  private final AgentService agentService;
  private final WorkflowService workflowService;
  private final RetrievalService retrievalService;
  private final CreateAgentService createAgentService;

  public ChatController(
      ChatService chatService,
      EmbeddingService embeddingService,
      ToolService toolService,
      AgentService agentService,
      WorkflowService workflowService,
      RetrievalService retrievalService,
      CreateAgentService createAgentService) {
    this.chatService = chatService;
    this.embeddingService = embeddingService;
    this.toolService = toolService;
    this.agentService = agentService;
    this.workflowService = workflowService;
    this.retrievalService = retrievalService;
    this.createAgentService = createAgentService;
  }

  /** POST /api/chat — LLM inference (operation: chat) */
  @PostMapping("/chat")
  public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
    if (isBlank(request.message())) {
      return badRequest("message is required");
    }
    return ResponseEntity.ok(chatService.chat(request.message()));
  }

  /** POST /api/embedding — Text embedding (operation: embeddings) */
  @PostMapping("/embedding")
  public ResponseEntity<?> embedding(@RequestBody EmbeddingRequest request) {
    if (isBlank(request.input())) {
      return badRequest("input is required");
    }
    String model = request.model() != null ? request.model() : "text-embedding-v3";
    return ResponseEntity.ok(embeddingService.embed(request.input(), model));
  }

  /** POST /api/tool — Tool execution (operation: execute_tool) */
  @PostMapping("/tool")
  public ResponseEntity<?> tool(@RequestBody ToolRequest request) {
    if (isBlank(request.name())) {
      return badRequest("name is required");
    }
    String arguments = request.arguments() != null ? request.arguments() : "{}";
    return ResponseEntity.ok(toolService.executeTool(request.name(), arguments));
  }

  /** POST /api/agent — Agent invocation (operation: invoke_agent) */
  @PostMapping("/agent")
  public ResponseEntity<?> agent(@RequestBody AgentRequest request) {
    if (isBlank(request.task())) {
      return badRequest("task is required");
    }
    String agentName = request.name() != null ? request.name() : "default-agent";
    return ResponseEntity.ok(agentService.invokeAgent(agentName, request.task()));
  }

  /** POST /api/workflow — Workflow orchestration (operation: invoke_workflow) */
  @PostMapping("/workflow")
  public ResponseEntity<?> workflow(@RequestBody WorkflowRequest request) {
    if (isBlank(request.input())) {
      return badRequest("input is required");
    }
    String workflowName = request.name() != null ? request.name() : "default-workflow";
    return ResponseEntity.ok(workflowService.runWorkflow(workflowName, request.input()));
  }

  /** POST /api/retrieval — Document retrieval (operation: retrieval) */
  @PostMapping("/retrieval")
  public ResponseEntity<?> retrieval(@RequestBody RetrievalRequest request) {
    if (isBlank(request.query())) {
      return badRequest("query is required");
    }
    String dataSourceId = request.dataSourceId() != null ? request.dataSourceId() : "default-index";
    int topK = request.topK() != null ? request.topK() : 5;
    return ResponseEntity.ok(retrievalService.retrieve(request.query(), dataSourceId, topK));
  }

  /** POST /api/create-agent — Agent creation (operation: create_agent) */
  @PostMapping("/create-agent")
  public ResponseEntity<?> createAgent(@RequestBody CreateAgentRequest request) {
    if (isBlank(request.name())) {
      return badRequest("name is required");
    }
    String description = request.description() != null ? request.description() : "";
    String instructions = request.instructions() != null ? request.instructions() : "You are a helpful assistant.";
    return ResponseEntity.ok(createAgentService.create(request.name(), description, instructions));
  }

  // --- Request DTOs ---

  public record ChatRequest(String message) {}
  public record EmbeddingRequest(String input, String model) {}
  public record ToolRequest(String name, String arguments) {}
  public record AgentRequest(String name, String task) {}
  public record WorkflowRequest(String name, String input) {}
  public record RetrievalRequest(String query, String dataSourceId, Integer topK) {}
  public record CreateAgentRequest(String name, String description, String instructions) {}

  // --- Helpers ---

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static ResponseEntity<Map<String, String>> badRequest(String msg) {
    return ResponseEntity.badRequest().body(Map.of("error", msg));
  }
}
