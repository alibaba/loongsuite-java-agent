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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.semconv.incubating.ErrorIncubatingAttributes;
import org.jspecify.annotations.Nullable;

/**
 * Package-private utility for applying common GenAI span and metric attributes.
 *
 * <p>Uses typed {@link io.opentelemetry.api.common.AttributeKey} constants from the OTel semconv
 * library for compile-time type safety.
 */
final class SpanAttributeHelper {

  private SpanAttributeHelper() {}

  /**
   * Applies the common GenAI attributes shared by all client-type invocations (inference,
   * embedding, agent).
   */
  static void applyCommonAttributes(
      Span span,
      String operationName,
      @Nullable String provider,
      @Nullable String requestModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    span.setAttribute(GEN_AI_OPERATION_NAME, operationName);
    if (provider != null) {
      span.setAttribute(GEN_AI_PROVIDER_NAME, provider);
    }
    if (requestModel != null) {
      span.setAttribute(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (serverAddress != null) {
      span.setAttribute(SERVER_ADDRESS, serverAddress);
    }
    if (serverPort != null) {
      span.setAttribute(SERVER_PORT, (long) serverPort);
    }
  }

  /**
   * Builds an {@link AttributesBuilder} pre-populated with the common metric attributes used by
   * duration and token histograms.
   *
   * @return a mutable builder that the caller can extend with operation-specific attributes
   */
  static AttributesBuilder commonMetricAttributes(
      String operationName,
      @Nullable String provider,
      @Nullable String requestModel,
      @Nullable String responseModel,
      @Nullable String serverAddress,
      @Nullable Integer serverPort,
      @Nullable String errorType) {
    AttributesBuilder builder = Attributes.builder();
    builder.put(GEN_AI_OPERATION_NAME, operationName);
    if (provider != null) {
      builder.put(GEN_AI_PROVIDER_NAME, provider);
    }
    if (requestModel != null) {
      builder.put(GEN_AI_REQUEST_MODEL, requestModel);
    }
    if (responseModel != null) {
      builder.put(GEN_AI_RESPONSE_MODEL, responseModel);
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
    return builder;
  }
}
