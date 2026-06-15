/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v2_2;

import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.List;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring Cloud Gateway 2.2 does not populate matched path predicate exchange attributes (added in
 * 3.0.5), so {@code http.route} falls back to route ID.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {Gateway22TestApplication.class})
class Gateway22RouteMappingTest extends AbstractRouteMappingTest {

  @Override
  protected String getSpanName() {
    return "POST";
  }

  @Override
  protected String getRandomUuidSpanName() {
    return "POST";
  }

  @Override
  protected String getFakeUuidSpanName(String routeId) {
    return "POST " + routeId;
  }

  @Override
  protected List<AttributeAssertion> getFakeUuidExpectedAttributes(String routeId) {
    return buildAttributeAssertions(routeId, "h1c://mock.fake", 0, 0);
  }
}
