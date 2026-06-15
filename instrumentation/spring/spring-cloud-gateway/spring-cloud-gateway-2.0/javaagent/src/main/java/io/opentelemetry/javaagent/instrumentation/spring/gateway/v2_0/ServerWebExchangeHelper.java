/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import static io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper.ROUTE_FILTER_SIZE_ATTRIBUTE;
import static io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper.ROUTE_ID_ATTRIBUTE;
import static io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper.ROUTE_ORDER_ATTRIBUTE;
import static io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper.ROUTE_URI_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.javaagent.instrumentation.spring.gateway.common.GatewayRouteHelper;
import javax.annotation.Nullable;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

public final class ServerWebExchangeHelper {

  /**
   * Exchange attribute for the matched path predicate pattern. Available on Spring Cloud Gateway
   * 3.0.5+; not present as a public constant on older versions compiled against here.
   */
  private static final String GATEWAY_PREDICATE_MATCHED_PATH_ATTR =
      "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayPredicateMatchedPathAttr";

  private static final String GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR =
      "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayPredicateMatchedPathRouteIdAttr";

  private ServerWebExchangeHelper() {}

  public static void extractAttributes(ServerWebExchange exchange, Context context) {
    // Record route info
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    if (route != null && GatewayRouteHelper.shouldCaptureExperimentalSpanAttributes()) {
      Span serverSpan = LocalRootSpan.fromContextOrNull(context);
      if (serverSpan == null) {
        return;
      }
      serverSpan.setAttribute(ROUTE_ID_ATTRIBUTE, route.getId());
      serverSpan.setAttribute(ROUTE_URI_ATTRIBUTE, route.getUri().toASCIIString());
      serverSpan.setAttribute(ROUTE_ORDER_ATTRIBUTE, route.getOrder());
      serverSpan.setAttribute(ROUTE_FILTER_SIZE_ATTRIBUTE, route.getFilters().size());
    }
  }

  @Nullable
  public static String extractServerRoute(@Nullable ServerWebExchange exchange) {
    if (exchange == null) {
      return null;
    }
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    if (route == null) {
      return null;
    }
    if (GatewayRouteHelper.usePathPatternForHttpRoute()) {
      String matchedPath = extractMatchedPathPattern(exchange, route);
      if (matchedPath != null) {
        return matchedPath;
      }
    }
    return GatewayRouteHelper.convergeRouteId(route.getId());
  }

  /**
   * Returns the path predicate pattern that matched this request, following the same validation
   * logic as {@link
   * org.springframework.cloud.gateway.support.tagsprovider.GatewayPathTagsProvider}.
   */
  @Nullable
  private static String extractMatchedPathPattern(ServerWebExchange exchange, Route route) {
    String matchedPathRouteId = exchange.getAttribute(GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR);
    String matchedPath = exchange.getAttribute(GATEWAY_PREDICATE_MATCHED_PATH_ATTR);
    if (route.getId().equals(matchedPathRouteId) && matchedPath != null) {
      return matchedPath;
    }
    return null;
  }
}
