/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.undertow;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.atomic.AtomicInteger;

/** Helper container for keeping track of request processing state in undertow. */
public final class UndertowActiveHandlers {
  private static final ContextKey<AtomicInteger> CONTEXT_KEY =
      ContextKey.named("opentelemetry-undertow-active-handlers");

  private UndertowActiveHandlers() {}

  /**
   * Attach to context.
   *
   * @param context server context
   * @param initialValue initial value for counter
   * @return new context
   */
  public static Context init(Context context, int initialValue) {
    return context.with(CONTEXT_KEY, new AtomicInteger(initialValue));
  }

  /**
   * Increment counter.
   *
   * @param context server context
   */
  public static void increment(Context context) {
    AtomicInteger integer = context.get(CONTEXT_KEY);
    if (integer != null) {
      integer.incrementAndGet();
    }
  }

  /**
   * Decrement counter.
   *
   * @param context server context
   * @return value of counter after decrementing it
   */
  public static int decrementAndGet(Context context) {
    AtomicInteger integer = context.get(CONTEXT_KEY);
    if(integer != null) {
      return integer.decrementAndGet();
    }
    return -1;
  }
}
