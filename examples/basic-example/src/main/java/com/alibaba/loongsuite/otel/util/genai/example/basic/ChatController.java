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

import java.util.Collections;
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
    if (isBlank(request.getMessage())) {
      return badRequest("message is required");
    }
    return ResponseEntity.ok(chatService.chat(request.getMessage()));
  }

  /** POST /api/embedding — Text embedding (operation: embeddings) */
  @PostMapping("/embedding")
  public ResponseEntity<?> embedding(@RequestBody EmbeddingRequest request) {
    if (isBlank(request.getInput())) {
      return badRequest("input is required");
    }
    String model = request.getModel() != null ? request.getModel() : "text-embedding-v3";
    return ResponseEntity.ok(embeddingService.embed(request.getInput(), model));
  }

  /** POST /api/tool — Tool execution (operation: execute_tool) */
  @PostMapping("/tool")
  public ResponseEntity<?> tool(@RequestBody ToolRequest request) {
    if (isBlank(request.getName())) {
      return badRequest("name is required");
    }
    String arguments = request.getArguments() != null ? request.getArguments() : "{}";
    return ResponseEntity.ok(toolService.executeTool(request.getName(), arguments));
  }

  /** POST /api/agent — Agent invocation (operation: invoke_agent) */
  @PostMapping("/agent")
  public ResponseEntity<?> agent(@RequestBody AgentRequest request) {
    if (isBlank(request.getTask())) {
      return badRequest("task is required");
    }
    String agentName = request.getName() != null ? request.getName() : "default-agent";
    return ResponseEntity.ok(agentService.invokeAgent(agentName, request.getTask()));
  }

  /** POST /api/workflow — Workflow orchestration (operation: invoke_workflow) */
  @PostMapping("/workflow")
  public ResponseEntity<?> workflow(@RequestBody WorkflowRequest request) {
    if (isBlank(request.getInput())) {
      return badRequest("input is required");
    }
    String workflowName = request.getName() != null ? request.getName() : "default-workflow";
    return ResponseEntity.ok(workflowService.runWorkflow(workflowName, request.getInput()));
  }

  /** POST /api/retrieval — Document retrieval (operation: retrieval) */
  @PostMapping("/retrieval")
  public ResponseEntity<?> retrieval(@RequestBody RetrievalRequest request) {
    if (isBlank(request.getQuery())) {
      return badRequest("query is required");
    }
    String dataSourceId =
        request.getDataSourceId() != null ? request.getDataSourceId() : "default-index";
    int topK = request.getTopK() != null ? request.getTopK() : 5;
    return ResponseEntity.ok(retrievalService.retrieve(request.getQuery(), dataSourceId, topK));
  }

  /** POST /api/create-agent — Agent creation (operation: create_agent) */
  @PostMapping("/create-agent")
  public ResponseEntity<?> createAgent(@RequestBody CreateAgentRequest request) {
    if (isBlank(request.getName())) {
      return badRequest("name is required");
    }
    String description = request.getDescription() != null ? request.getDescription() : "";
    String instructions =
        request.getInstructions() != null
            ? request.getInstructions()
            : "You are a helpful assistant.";
    return ResponseEntity.ok(
        createAgentService.create(request.getName(), description, instructions));
  }

  // --- Request DTOs ---

  public static class ChatRequest {
    private String message;

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }
  }

  public static class EmbeddingRequest {
    private String input;
    private String model;

    public String getInput() {
      return input;
    }

    public void setInput(String input) {
      this.input = input;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }
  }

  public static class ToolRequest {
    private String name;
    private String arguments;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getArguments() {
      return arguments;
    }

    public void setArguments(String arguments) {
      this.arguments = arguments;
    }
  }

  public static class AgentRequest {
    private String name;
    private String task;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getTask() {
      return task;
    }

    public void setTask(String task) {
      this.task = task;
    }
  }

  public static class WorkflowRequest {
    private String name;
    private String input;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getInput() {
      return input;
    }

    public void setInput(String input) {
      this.input = input;
    }
  }

  public static class RetrievalRequest {
    private String query;
    private String dataSourceId;
    private Integer topK;

    public String getQuery() {
      return query;
    }

    public void setQuery(String query) {
      this.query = query;
    }

    public String getDataSourceId() {
      return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
      this.dataSourceId = dataSourceId;
    }

    public Integer getTopK() {
      return topK;
    }

    public void setTopK(Integer topK) {
      this.topK = topK;
    }
  }

  public static class CreateAgentRequest {
    private String name;
    private String description;
    private String instructions;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getInstructions() {
      return instructions;
    }

    public void setInstructions(String instructions) {
      this.instructions = instructions;
    }
  }

  // --- Helpers ---

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static ResponseEntity<Map<String, String>> badRequest(String msg) {
    return ResponseEntity.badRequest().body(Collections.singletonMap("error", msg));
  }
}
