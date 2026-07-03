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

package com.alibaba.loongsuite.otel.util.genai;

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;

import io.opentelemetry.api.trace.Span;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/** Mutable {@link CompletionHookContext} that can update message lists during hook processing. */
final class MutableCompletionHookContext implements CompletionHookContext {

  private List<InputMessage> inputs;
  private List<OutputMessage> outputs;
  private final List<MessagePart> systemInstruction;
  private final @Nullable List<ToolDefinition> toolDefinitions;
  private final Span span;
  private final @Nullable EventLogRecord logRecord;
  private final @Nullable MessageContentCapable messageContentCapable;
  private final long startTimeEpochMillis;

  MutableCompletionHookContext(
      List<InputMessage> inputs,
      List<OutputMessage> outputs,
      List<MessagePart> systemInstruction,
      @Nullable List<ToolDefinition> toolDefinitions,
      Span span,
      @Nullable EventLogRecord logRecord,
      @Nullable MessageContentCapable messageContentCapable,
      long startTimeEpochMillis) {
    this.inputs = new ArrayList<>(inputs);
    this.outputs = new ArrayList<>(outputs);
    this.systemInstruction = systemInstruction;
    this.toolDefinitions = toolDefinitions;
    this.span = span;
    this.logRecord = logRecord;
    this.messageContentCapable = messageContentCapable;
    this.startTimeEpochMillis = startTimeEpochMillis;
  }

  static MutableCompletionHookContext forInference(
      InferenceInvocation invocation, @Nullable EventLogRecord logRecord) {
    return new MutableCompletionHookContext(
        invocation.getInputMessages(),
        invocation.getOutputMessages(),
        invocation.getSystemInstruction(),
        invocation.getToolDefinitions().isEmpty() ? null : invocation.getToolDefinitions(),
        invocation.getSpan(),
        logRecord,
        invocation,
        invocation.getStartTimeEpochMillis());
  }

  static MutableCompletionHookContext forAgent(
      AgentInvocation invocation, @Nullable EventLogRecord logRecord) {
    return new MutableCompletionHookContext(
        invocation.getInputMessages(),
        invocation.getOutputMessages(),
        invocation.getSystemInstruction(),
        invocation.getToolDefinitions().isEmpty() ? null : invocation.getToolDefinitions(),
        invocation.getSpan(),
        logRecord,
        invocation,
        invocation.getStartTimeEpochMillis());
  }

  static MutableCompletionHookContext forWorkflow(
      WorkflowInvocation invocation, @Nullable EventLogRecord logRecord) {
    return new MutableCompletionHookContext(
        invocation.getInputMessages(),
        invocation.getOutputMessages(),
        Collections.emptyList(),
        null,
        invocation.getSpan(),
        logRecord,
        invocation,
        invocation.getStartTimeEpochMillis());
  }

  @Nullable
  MessageContentCapable getMessageContentCapable() {
    return messageContentCapable;
  }

  long getStartTimeEpochMillis() {
    return startTimeEpochMillis;
  }

  void replaceInputs(List<InputMessage> newInputs) {
    this.inputs = new ArrayList<>(newInputs);
  }

  void replaceOutputs(List<OutputMessage> newOutputs) {
    this.outputs = new ArrayList<>(newOutputs);
  }

  @Override
  public List<InputMessage> getInputs() {
    return inputs;
  }

  @Override
  public List<OutputMessage> getOutputs() {
    return outputs;
  }

  @Override
  public List<MessagePart> getSystemInstruction() {
    return systemInstruction;
  }

  @Override
  public @Nullable List<ToolDefinition> getToolDefinitions() {
    return toolDefinitions;
  }

  @Override
  public Span getSpan() {
    return span;
  }

  @Override
  public @Nullable EventLogRecord getLogRecord() {
    return logRecord;
  }
}
