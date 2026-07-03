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
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_PORT;

import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;

import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI create-agent operation.
 *
 * <p>SpanKind is {@code CLIENT}. The handler sets this when creating the span.
 *
 * <p>System instructions are only captured on the span when content capturing is enabled via {@link
 * GenAiConfigUtil#shouldCaptureContentOnSpans()}.
 */
public final class CreateAgentInvocation extends GenAiInvocation {

  private final String provider;
  private final @Nullable String requestModel;
  private final @Nullable String agentName;
  private final @Nullable String serverAddress;
  private final @Nullable Integer serverPort;

  // Agent-specific attributes
  private @Nullable String agentId;
  private @Nullable String agentDescription;
  private @Nullable String agentVersion;

  // Content
  private List<MessagePart> systemInstruction = Collections.emptyList();

  CreateAgentInvocation(
      GenAiTelemetryHandler handler,
      Span span,
      Scope scope,
      String provider,
      @Nullable String requestModel,
      @Nullable String agentName,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    super(handler, span, scope);
    this.provider = provider;
    this.requestModel = requestModel;
    this.agentName = agentName;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
  }

  // ---------------------------------------------------------------------------
  // Setters
  // ---------------------------------------------------------------------------

  /** Sets the agent identifier. */
  public void setAgentId(@Nullable String agentId) {
    this.agentId = agentId;
  }

  /** Sets the agent description. */
  public void setAgentDescription(@Nullable String agentDescription) {
    this.agentDescription = agentDescription;
  }

  /** Sets the agent version. */
  public void setAgentVersion(@Nullable String agentVersion) {
    this.agentVersion = agentVersion;
  }

  /** Sets the system instructions (opt-in content, requires content capturing). */
  public void setSystemInstruction(List<MessagePart> systemInstruction) {
    this.systemInstruction = systemInstruction;
  }

  // ---------------------------------------------------------------------------
  // Package-private getters (for handler)
  // ---------------------------------------------------------------------------

  @Nullable String getAgentName() {
    return agentName;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return "create_agent";
  }

  @Override
  protected String spanKindValue() {
    return GenAiSpanKindValues.AGENT;
  }

  @Override
  protected String spanName() {
    return agentName != null ? "create_agent " + agentName : "create_agent";
  }

  @Override
  protected void applyAttributes() {
    SpanAttributeHelper.applyCommonAttributes(
        span, operationName(), provider, requestModel, serverAddress, serverPort);

    // Agent-specific attributes
    if (agentName != null) {
      span.setAttribute(GEN_AI_AGENT_NAME, agentName);
    }
    if (agentId != null) {
      span.setAttribute(GEN_AI_AGENT_ID, agentId);
    }
    if (agentDescription != null) {
      span.setAttribute(GEN_AI_AGENT_DESCRIPTION, agentDescription);
    }
    if (agentVersion != null) {
      span.setAttribute(GEN_AI_AGENT_VERSION, agentVersion);
    }

    // System instructions are opt-in content
    if (GenAiConfigUtil.shouldCaptureContentOnSpans()) {
      if (!systemInstruction.isEmpty()) {
        span.setAttribute(
            GenAiAttributes.GEN_AI_SYSTEM_INSTRUCTIONS,
            GenAiContentSerializer.toValue(systemInstruction));
      }
    }
  }

  @Override
  protected Attributes buildMetricAttributes() {
    AttributesBuilder builder = Attributes.builder();
    builder.put(GEN_AI_OPERATION_NAME, operationName());
    builder.put(GEN_AI_PROVIDER_NAME, provider);
    if (requestModel != null) {
      builder.put(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (serverAddress != null) {
      builder.put(SERVER_ADDRESS, serverAddress);
    }
    if (serverPort != null) {
      builder.put(SERVER_PORT, (long) serverPort);
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
