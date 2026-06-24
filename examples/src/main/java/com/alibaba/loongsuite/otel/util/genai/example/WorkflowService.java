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
import java.util.Collections;
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
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new TextPart(userInput)))));

      List<StepResult> steps = new ArrayList<>();

      // Step 1: analyze user intent via LLM
      ChatService.ChatResponse analysis =
          chatService.chat("Analyze this request and identify what tools are needed: " + userInput);
      steps.add(new StepResult("analyze", analysis.getContent()));

      // Step 2: execute a tool based on the analysis
      ToolService.ToolResponse toolResult =
          toolService.executeTool("get_time", "{}");
      steps.add(new StepResult("tool:" + toolResult.getTool(), toolResult.getResult()));

      // Step 3: synthesize final answer via LLM
      String synthesisPrompt =
          "Based on the analysis: [" + analysis.getContent()
              + "] and tool result: [" + toolResult.getResult()
              + "], provide a final answer to: " + userInput;
      ChatService.ChatResponse synthesis = chatService.chat(synthesisPrompt);
      steps.add(new StepResult("synthesize", synthesis.getContent()));

      wf.setOutputMessages(
          Collections.singletonList(
              new OutputMessage(
                  "assistant",
                  Collections.singletonList(new TextPart(synthesis.getContent())),
                  "stop")));

      return new WorkflowResponse(workflowName, synthesis.getContent(), steps);
    }
  }

  public static class StepResult {
    private final String step;
    private final String output;

    public StepResult(String step, String output) {
      this.step = step;
      this.output = output;
    }

    public String getStep() { return step; }
    public String getOutput() { return output; }
  }

  public static class WorkflowResponse {
    private final String workflow;
    private final String finalAnswer;
    private final List<StepResult> steps;

    public WorkflowResponse(String workflow, String finalAnswer, List<StepResult> steps) {
      this.workflow = workflow;
      this.finalAnswer = finalAnswer;
      this.steps = steps;
    }

    public String getWorkflow() { return workflow; }
    public String getFinalAnswer() { return finalAnswer; }
    public List<StepResult> getSteps() { return steps; }
  }
}
