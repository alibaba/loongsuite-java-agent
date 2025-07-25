/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ConfiguredTraceAnnotationsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testMethodWithDisabledNewRelicAnnotationShouldBeIgnored() {
    SayTracedHello.fromCallableWhenDisabled();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void testMethodWithAnnotationShouldBeTraced() {
    assertThat(new AnnotationTracedCallable().call()).isEqualTo("Hello!");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("AnnotationTracedCallable.call")
                        .hasAttributesSatisfyingExactly(
                            codeFunctionAssertions(AnnotationTracedCallable.class, "call"))));
  }

  static class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    public String call() {
      return "Hello!";
    }
  }
}
