/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpServerRouteListenersTest {

  @Test
  void testHttpServerRouteCustomizerProvider() {
    AtomicReference<String> capturedSpanName = new AtomicReference<>();

    // Step 1: Create a customizer provider
    HttpServerRouteCustomizerProvider userProvider =
        customizer ->
            customizer.addSpanNameUpdateListener(
                (context, httpServerSpan, newSpanName) -> {
                  capturedSpanName.set(newSpanName);
                });

    // Step 2: Simulate the framework calling the provider
    List<HttpServerSpanNameUpdateListener> listeners = new ArrayList<>();
    HttpServerRouteCustomizer customizer = listeners::add;
    userProvider.customize(customizer);

    // Step 3: Verify the listener was registered
    assertThat(listeners).hasSize(1);

    // Step 4: Simulate a span name update
    listeners.get(0).onSpanNameUpdated(Context.root(), mock(Span.class), "POST /api/users");

    // Step 5: Verify the user's listener received the update
    assertThat(capturedSpanName.get()).isEqualTo("POST /api/users");
  }
}
