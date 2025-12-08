/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

/**
 * Listener interface for HTTP server span name updates.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface HttpServerSpanNameUpdateListener {

  /**
   * Called when an HTTP server span name is updated.
   *
   * @param context the execution context where the update occurred
   * @param httpServerSpan the HTTP server span that was updated
   * @param newSpanName the new span name
   */
  void onSpanNameUpdated(Context context, Span httpServerSpan, String newSpanName);
}
