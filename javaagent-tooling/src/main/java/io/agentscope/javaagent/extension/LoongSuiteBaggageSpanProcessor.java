/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.agentscope.javaagent.extension;

import static java.util.Collections.emptySet;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import java.util.Map;
import java.util.Set;

public class LoongSuiteBaggageSpanProcessor implements SpanProcessor {

  private final Set<String> allowedPrefixes;

  private final Set<String> stripPrefixes;

  private final boolean allowAll;

  @Override
  public void onStart(Context context, ReadWriteSpan readWriteSpan) {
    Baggage baggage = Baggage.fromContext(context);

    for (Map.Entry<String, BaggageEntry> entry : baggage.asMap().entrySet()) {
      if (!shouldProcessKey(entry.getKey())) {
        continue;
      }
      String attributeKey = stripPrefix(entry.getKey());
      readWriteSpan.setAttribute(attributeKey, entry.getValue().getValue());
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan readableSpan) {
    // do nothing
  }

  @Override
  public boolean isEndRequired() {
    return false;
  }

  public static LoongSuiteBaggageSpanProcessor create(
      Set<String> allowedPrefixes, Set<String> stripPrefixes) {
    if (allowedPrefixes == null) {
      allowedPrefixes = emptySet();
    }
    if (stripPrefixes == null) {
      stripPrefixes = emptySet();
    }
    return new LoongSuiteBaggageSpanProcessor(
        allowedPrefixes, stripPrefixes, allowedPrefixes.isEmpty());
  }

  private boolean shouldProcessKey(String key) {
    if (allowAll) {
      return true;
    }

    for (String prefix : allowedPrefixes) {
      if (key.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }

  private String stripPrefix(String key) {
    for (String prefix : stripPrefixes) {
      if (key.startsWith(prefix)) {
        return key.substring(prefix.length());
      }
    }
    return key;
  }

  private LoongSuiteBaggageSpanProcessor(
      Set<String> allowedPrefixes, Set<String> stripPrefixes, boolean allowAll) {
    this.allowedPrefixes = allowedPrefixes;
    this.stripPrefixes = stripPrefixes;
    this.allowAll = allowAll;
  }
}
