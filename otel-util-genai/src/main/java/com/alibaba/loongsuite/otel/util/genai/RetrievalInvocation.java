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

import com.alibaba.loongsuite.otel.util.genai.types.RetrievalDocument;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Invocation representing a GenAI retrieval operation.
 *
 * <p>SpanKind is {@code CLIENT}. The handler sets this when creating the span.
 *
 * <p>Retrieval query text and documents are only captured on the span when content capturing is
 * enabled via {@link GenAiConfigUtil#shouldCaptureContentOnSpans()}.
 */
public final class RetrievalInvocation extends GenAiInvocation {

  private final @Nullable String provider;
  private final @Nullable String dataSourceId;
  private @Nullable String requestModel;
  private @Nullable String serverAddress;
  private @Nullable Integer serverPort;

  // Retrieval-specific attributes
  private @Nullable Double topK;
  private @Nullable String queryText;
  private List<?> documents = Collections.emptyList();

  RetrievalInvocation(
      GenAiTelemetryHandler handler,
      Span span,
      Scope scope,
      @Nullable String provider,
      @Nullable String dataSourceId,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    super(handler, span, scope);
    this.provider = provider;
    this.dataSourceId = dataSourceId;
    this.requestModel = requestModel;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
  }

  // ---------------------------------------------------------------------------
  // Setters
  // ---------------------------------------------------------------------------

  /** Sets the requested model name for the retrieval operation. */
  public void setRequestModel(@Nullable String requestModel) {
    this.requestModel = requestModel;
  }

  /** Sets the server address for the retrieval backend. */
  public void setServerAddress(@Nullable String serverAddress) {
    this.serverAddress = serverAddress;
  }

  /** Sets the server port for the retrieval backend. */
  public void setServerPort(@Nullable Integer serverPort) {
    this.serverPort = serverPort;
  }

  /** Sets the requested top-k value for the retrieval operation. */
  public void setTopK(@Nullable Double topK) {
    this.topK = topK;
  }

  /** Sets the retrieval query text (opt-in content, requires content capturing). */
  public void setQueryText(@Nullable String queryText) {
    this.queryText = queryText;
  }

  /** Sets the retrieved documents as typed records (opt-in content, requires content capturing). */
  public void setDocuments(List<RetrievalDocument> documents) {
    this.documents = documents;
  }

  /**
   * Sets the retrieved documents as arbitrary maps (opt-in content, requires content capturing).
   * Supports documents with custom fields beyond the standard {@link RetrievalDocument} record.
   */
  public void setDocumentMaps(List<Map<String, Object>> documents) {
    this.documents = documents;
  }

  // ---------------------------------------------------------------------------
  // GenAiInvocation overrides
  // ---------------------------------------------------------------------------

  @Override
  protected String operationName() {
    return "retrieval";
  }

  @Override
  protected String spanKindValue() {
    return GenAiSpanKindValues.RETRIEVER;
  }

  @Override
  protected String spanName() {
    return dataSourceId != null ? "retrieval " + dataSourceId : "retrieval";
  }

  @Override
  protected void applyAttributes() {
    SpanAttributeHelper.applyCommonAttributes(
        span, operationName(), provider, requestModel, serverAddress, serverPort);

    if (dataSourceId != null) {
      span.setAttribute(GEN_AI_DATA_SOURCE_ID, dataSourceId);
    }
    if (topK != null) {
      span.setAttribute(GEN_AI_REQUEST_TOP_K, topK);
    }

    // Query text and documents are opt-in content (requires experimental mode)
    if (GenAiConfigUtil.isExperimentalMode()
        && span.isRecording()
        && GenAiConfigUtil.shouldCaptureContentOnSpans()) {
      if (queryText != null) {
        span.setAttribute(GEN_AI_RETRIEVAL_QUERY_TEXT, queryText);
      }
      if (!documents.isEmpty()) {
        span.setAttribute(
            GenAiAttributes.GEN_AI_RETRIEVAL_DOCUMENTS,
            Value.of(GenAiContentSerializer.toJsonString(documents)));
      }
    }
  }

  @Override
  protected Attributes buildMetricAttributes() {
    AttributesBuilder builder = Attributes.builder();
    builder.put(GEN_AI_OPERATION_NAME, operationName());
    if (provider != null) {
      builder.put(GEN_AI_PROVIDER_NAME, provider);
    }
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
