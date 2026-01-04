/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.agentscope.javaagent.extension;

import static java.util.Collections.emptySet;

import com.google.auto.service.AutoService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import java.util.HashSet;
import java.util.Set;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class BaggageSpanProcessorConfigurer implements AutoConfigurationCustomizerProvider {

  private static final String LOONGSUITE_PROCESSOR_BAGGAGE_ALLOWED_PREFIXES =
      "loongsuite.processor.baggage-allowed-prefixes";

  private static final String LOONGSUITE_PROCESSOR_BAGGAGE_STRIP_PREFIXES =
      "loongsuite.processor.baggage-strip-prefixes";

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        BaggageSpanProcessorConfigurer::configure);
  }

  @CanIgnoreReturnValue
  private static SdkTracerProviderBuilder configure(
      SdkTracerProviderBuilder tracerProviderBuilder, ConfigProperties config) {

    String allowedPrefixesStr = config.getString(LOONGSUITE_PROCESSOR_BAGGAGE_ALLOWED_PREFIXES);
    if (allowedPrefixesStr == null || allowedPrefixesStr.trim().isEmpty()) {
      return tracerProviderBuilder;
    }
    Set<String> allowedPrefixes = parsePrefixes(allowedPrefixesStr);

    String stripPrefixesStr = config.getString(LOONGSUITE_PROCESSOR_BAGGAGE_STRIP_PREFIXES);
    Set<String> stripPrefixes = parsePrefixes(stripPrefixesStr);

    tracerProviderBuilder.addSpanProcessor(
        LoongSuiteBaggageSpanProcessor.create(allowedPrefixes, stripPrefixes));

    return tracerProviderBuilder;
  }

  private static Set<String> parsePrefixes(String prefixesStr) {
    if (prefixesStr == null || prefixesStr.trim().isEmpty()) {
      return emptySet();
    }

    String[] prefixesArray = prefixesStr.split(",");
    Set<String> prefixes = new HashSet<>(prefixesArray.length);

    for (String prefix : prefixesArray) {
      if (prefix != null && !prefix.trim().isEmpty()) {
        prefixes.add(prefix.trim());
      }
    }
    return prefixes;
  }
}
