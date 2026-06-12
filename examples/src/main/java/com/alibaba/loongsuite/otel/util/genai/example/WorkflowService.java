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
import com.alibaba.loongsuite.otel.util.genai.WorkflowInvocation;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {

  private final GenAiTelemetryHandler handler;
  private final ChatService chatService;
  private final ToolService toolService;

  public WorkflowService(
      GenAiTelemetryHandler handler, ChatService chatService, ToolService toolService) {
    this.handler = handler;
    this.chatService = chatService;
    this.toolService = toolService;
  }

  public WorkflowResponse runWorkflow(String workflowName, String userInput) {
    try (WorkflowInvocation wf = handler.workflow(workflowName)) {
      wf.setInputMessages(
          List.of(new InputMessage("user", List.of(new TextPart(userInput)))));

      List<StepResult> steps = new ArrayList<>();

      // Step 1: analyze user intent via LLM
      ChatService.ChatResponse analysis =
          chatService.chat("Analyze this request and identify what tools are needed: " + userInput);
      steps.add(new StepResult("analyze", analysis.content()));

      // Step 2: execute a tool based on the analysis
      ToolService.ToolResponse toolResult =
          toolService.executeTool("get_time", "{}");
      steps.add(new StepResult("tool:" + toolResult.tool(), toolResult.result()));

      // Step 3: synthesize final answer via LLM
      String synthesisPrompt =
          "Based on the analysis: [" + analysis.content()
              + "] and tool result: [" + toolResult.result()
              + "], provide a final answer to: " + userInput;
      ChatService.ChatResponse synthesis = chatService.chat(synthesisPrompt);
      steps.add(new StepResult("synthesize", synthesis.content()));

      wf.setOutputMessages(
          List.of(
              new OutputMessage(
                  "assistant",
                  List.of(new TextPart(synthesis.content())),
                  "stop")));

      return new WorkflowResponse(workflowName, synthesis.content(), steps);
    }
  }

  public record StepResult(String step, String output) {}

  public record WorkflowResponse(
      String workflow, String finalAnswer, List<StepResult> steps) {}
}
