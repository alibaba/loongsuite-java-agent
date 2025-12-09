/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

/**
 * A service provider interface (SPI) for customizing HTTP server route behavior.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface HttpServerRouteCustomizerProvider {

  /**
   * Customizes HTTP server route behavior.
   *
   * <p>This method is called during initialization to allow providers to register listeners for
   * HTTP server span name updates.
   *
   * @param customizer the customizer for HTTP server route behavior
   */
  void customize(HttpServerRouteCustomizer customizer);
}
