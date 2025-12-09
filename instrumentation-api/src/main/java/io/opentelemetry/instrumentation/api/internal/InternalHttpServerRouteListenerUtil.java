/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalHttpServerRouteListenerUtil {

  private static final Logger logger =
      Logger.getLogger(InternalHttpServerRouteListenerUtil.class.getName());

  private static volatile SpanNameUpdateNotifier notifier = NoOpNotifier.INSTANCE;

  static {
    try {
      // Try to initialize the incubator module
      Class.forName(
          "io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerRouteListenerUtil");
    } catch (ClassNotFoundException e) {
      // Incubator API not available, this is fine
      logger.log(Level.FINE, "HttpServerRouteListener incubator API not available");
    }
  }

  /**
   * Sets the notifier from the incubator module.
   *
   * @param notifier the notifier to set
   */
  public static void setNotifier(SpanNameUpdateNotifier notifier) {
    InternalHttpServerRouteListenerUtil.notifier =
        notifier != null ? notifier : NoOpNotifier.INSTANCE;
  }

  /**
   * Notifies listeners about span name update.
   *
   * @param context the execution context
   * @param span the HTTP server span
   * @param newSpanName the new span name
   */
  public static void notifySpanNameUpdate(Context context, Span span, String newSpanName) {
    notifier.onSpanNameUpdated(context, span, newSpanName);
  }

  /**
   * Interface for span name update notifications.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public interface SpanNameUpdateNotifier {
    void onSpanNameUpdated(Context context, Span span, String newSpanName);
  }

  private enum NoOpNotifier implements SpanNameUpdateNotifier {
    INSTANCE;

    @Override
    public void onSpanNameUpdated(Context context, Span span, String newSpanName) {
      // No-op implementation when incubator is not available
    }
  }

  private InternalHttpServerRouteListenerUtil() {}
}
