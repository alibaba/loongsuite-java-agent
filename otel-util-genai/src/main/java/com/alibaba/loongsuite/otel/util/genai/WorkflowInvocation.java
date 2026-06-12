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

import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*;

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI workflow invocation.
 *
 * <p>SpanKind is {@code INTERNAL}. The handler sets this when creating the span.
 */
public final class WorkflowInvocation extends GenAiInvocation {

  private final @Nullable String name;

  private List<InputMessage> inputMessages = Collections.emptyList();
  private List<OutputMessage> outputMessages = Collections.emptyList();

  WorkflowInvocation(
      GenAiTelemetryHandler handler, Span span, Scope scope, @Nullable String name) {
    super(handler, span, scope);
    this.name = name;
  }

  // ---------------------------------------------------------------------------
  // Setters
  // ---------------------------------------------------------------------------

  public void setInputMessages(List<InputMessage> inputMessages) {
    this.inputMessages = inputMessages;
  }

  public void setOutputMessages(List<OutputMessage> outputMessages) {
    this.outputMessages = outputMessages;
  }

  // ---------------------------------------------------------------------------
  // Package-private getters (for handler)
  // ---------------------------------------------------------------------------

  @Nullable
  String getName() {
    return name;
  }

  List<InputMessage> getInputMessages() {
    return inputMessages;
  }

  List<OutputMessage> getOutputMessages() {
    return outputMessages;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return "invoke_workflow";
  }

  @Override
  protected String spanName() {
    return name != null ? "invoke_workflow " + name : "invoke_workflow";
  }

  @Override
  protected void applyAttributes() {
    span.setAttribute(GEN_AI_OPERATION_NAME, operationName());
    if (name != null) {
      span.setAttribute(GEN_AI_WORKFLOW_NAME, name);
    }

    AttributesBuilder contentBuilder = Attributes.builder();
    GenAiContentAttributes.appendWorkflowContent(
        contentBuilder, inputMessages, outputMessages);
    span.setAllAttributes(contentBuilder.build());
  }

  @Override
  protected Attributes buildMetricAttributes() {
    AttributesBuilder builder = Attributes.builder();
    builder.put(GEN_AI_OPERATION_NAME, operationName());
    if (name != null) {
      builder.put(GEN_AI_WORKFLOW_NAME, name);
    }
    if (errorType != null) {
      builder.put(ErrorIncubatingAttributes.ERROR_TYPE, errorType);
    }
    return builder.build();
  }

  @Override
  protected long getInputTokens() {
    return -1;
  }

  @Override
  protected long getOutputTokens() {
    return -1;
  }
}
