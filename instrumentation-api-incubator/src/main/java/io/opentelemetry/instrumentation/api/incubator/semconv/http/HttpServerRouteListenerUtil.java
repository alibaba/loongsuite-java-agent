/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InternalHttpServerRouteListenerUtil;
import io.opentelemetry.instrumentation.api.internal.ServiceLoaderUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpServerRouteListenerUtil {

  static {
    try {
      List<HttpServerSpanNameUpdateListener> allListeners = new ArrayList<>();
      HttpServerRouteCustomizer customizer = new HttpServerRouteCustomizerImpl(allListeners);

      for (HttpServerRouteCustomizerProvider provider :
          ServiceLoaderUtil.load(HttpServerRouteCustomizerProvider.class)) {
        provider.customize(customizer);
      }

      HttpServerRouteListeners.setListeners(allListeners);
      InternalHttpServerRouteListenerUtil.setNotifier(new NotifierAdapter());

    } catch (RuntimeException e) {
      // Ignore
    }
  }

  private HttpServerRouteListenerUtil() {}

  private static final class HttpServerRouteCustomizerImpl implements HttpServerRouteCustomizer {
    private final List<HttpServerSpanNameUpdateListener> listeners;

    HttpServerRouteCustomizerImpl(List<HttpServerSpanNameUpdateListener> listeners) {
      this.listeners = listeners;
    }

    @Override
    public void addSpanNameUpdateListener(HttpServerSpanNameUpdateListener listener) {
      listeners.add(listener);
    }
  }

  private static final class NotifierAdapter
      implements InternalHttpServerRouteListenerUtil.SpanNameUpdateNotifier {
    @Override
    public void onSpanNameUpdated(Context context, Span span, String newSpanName) {
      HttpServerRouteListeners.onHttpServerSpanNameUpdated(context, span, newSpanName);
    }
  }
}
