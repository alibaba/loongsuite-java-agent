/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for managing HTTP server route listeners. This API is experimental and may change
 * or be removed in future versions.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class HttpServerRouteListeners {
  private static volatile List<HttpServerSpanNameUpdateListener> listeners =
      Collections.emptyList();

  private HttpServerRouteListeners() {}

  /**
   * Notifies all registered listeners when an HTTP server span name is updated.
   *
   * @param context the execution context
   * @param httpServerSpan the HTTP server span
   * @param newSpanName the new span name
   */
  public static void onHttpServerSpanNameUpdated(
      Context context, Span httpServerSpan, String newSpanName) {
    for (HttpServerSpanNameUpdateListener listener : listeners) {
      listener.onSpanNameUpdated(context, httpServerSpan, newSpanName);
    }
  }

  static void setListeners(List<HttpServerSpanNameUpdateListener> newListeners) {
    listeners = Collections.unmodifiableList(newListeners);
  }
}
