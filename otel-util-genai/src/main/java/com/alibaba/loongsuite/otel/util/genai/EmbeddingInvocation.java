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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI embedding call.
 *
 * <p>SpanKind is {@code CLIENT}. The handler sets this when creating the span.
 */
public final class EmbeddingInvocation extends GenAiInvocation {

  private final String provider;
  private final @Nullable String requestModel;
  private final @Nullable String serverAddress;
  private final @Nullable Integer serverPort;

  private @Nullable List<String> encodingFormats;
  private @Nullable Long inputTokens;
  private @Nullable Long dimensionCount;
  private @Nullable String responseModel;

  EmbeddingInvocation(
      GenAiTelemetryHandler handler,
      Span span,
      Scope scope,
      String provider,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    super(handler, span, scope);
    this.provider = provider;
    this.requestModel = requestModel;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
  }

  // ---------------------------------------------------------------------------
  // Setters
  // ---------------------------------------------------------------------------

  public void setEncodingFormats(@Nullable List<String> encodingFormats) {
    this.encodingFormats = encodingFormats;
  }

  public void setInputTokens(@Nullable Long inputTokens) {
    this.inputTokens = inputTokens;
  }

  public void setDimensionCount(@Nullable Long dimensionCount) {
    this.dimensionCount = dimensionCount;
  }

  public void setResponseModel(@Nullable String responseModel) {
    this.responseModel = responseModel;
  }

  // ---------------------------------------------------------------------------
  // Package-private getters
  // ---------------------------------------------------------------------------

  @Nullable
  String getResponseModel() {
    return responseModel;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return "embeddings";
  }

  @Override
  protected String spanName() {
    return requestModel != null ? "embeddings " + requestModel : "embeddings";
  }

  @Override
  protected void applyAttributes() {
    SpanAttributeHelper.applyCommonAttributes(
        span, operationName(), provider, requestModel, serverAddress, serverPort);

    if (encodingFormats != null) {
      span.setAttribute(GEN_AI_REQUEST_ENCODING_FORMATS, encodingFormats);
    }
    if (dimensionCount != null) {
      span.setAttribute(GEN_AI_EMBEDDINGS_DIMENSION_COUNT, dimensionCount);
    }
    if (responseModel != null) {
      span.setAttribute(GEN_AI_RESPONSE_MODEL, responseModel);
    }
    if (inputTokens != null) {
      span.setAttribute(GEN_AI_USAGE_INPUT_TOKENS, inputTokens);
    }
  }

  @Override
  protected Attributes buildMetricAttributes() {
    return SpanAttributeHelper.commonMetricAttributes(
            operationName(),
            provider,
            requestModel,
            responseModel,
            serverAddress,
            serverPort,
            errorType)
        .build();
  }

  @Override
  protected long getInputTokens() {
    return inputTokens != null ? inputTokens : -1;
  }

  @Override
  protected long getOutputTokens() {
    return -1;
  }
}
