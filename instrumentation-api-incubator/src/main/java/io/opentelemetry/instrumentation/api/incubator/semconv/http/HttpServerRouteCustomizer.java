/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

/**
 * Provides customizations for HTTP server route behavior.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface HttpServerRouteCustomizer {

  /**
   * Adds a listener to be notified when HTTP server span names are updated.
   *
   * @param listener the listener to add
   */
  void addSpanNameUpdateListener(HttpServerSpanNameUpdateListener listener);
}
