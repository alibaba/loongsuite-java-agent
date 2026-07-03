/*
 * Copyright 2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.loongsuite.otel.util.genai.example.common;

import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * Spring-side OTel / GenAI bootstrap.
 *
 * <p>Three steps before instrumenting:
 * <ol>
 *   <li>Configure {@code otel.*} / {@code otel.instrumentation.genai.*} (see application.yml in each example)
 *   <li>Inject {@link GenAiTelemetryHandler} — factory for all GenAI spans
 *   <li>Open an Invocation in try-with-resources; close ends the span automatically
 * </ol>
 *
 * <p>Multimodal blob upload (BlobPart → external storage → UriPart) is optional. It is enabled
 * only when {@code otel.instrumentation.genai.multimodal.storage.base.path} and upload mode are
 * configured; the library registers {@code MultimodalCompletionHook} automatically — no app code
 * beyond using BlobPart in messages. See application.yml {@code multimodal.*} settings.
 */
@Configuration
public class GenAiConfig {

  @Bean
  public OpenTelemetry openTelemetry(Environment environment) {
    // Bridge otel.* and SLS credentials from application.yml into System properties
    bridgeOtelProperties(environment);
    bridgeSlsCredentials(environment);
    return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
  }

  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("loongsuite-genai-examples");
  }

  /** Entry point: {@code GenAiTelemetryHandler.create(openTelemetry)}; business code depends on this bean only. */
  @Bean
  public GenAiTelemetryHandler genAiTelemetryHandler(OpenTelemetry openTelemetry) {
    return GenAiTelemetryHandler.create(openTelemetry);
  }

  @Bean
  public String genAiServerAddress(@Value("${genai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {
    return URI.create(baseUrl).getHost();
  }

  @Bean
  public Integer genAiServerPort(@Value("${genai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl) {
    URI uri = URI.create(baseUrl);
    int port = uri.getPort();
    if (port > 0) {
      return port;
    }
    return "https".equals(uri.getScheme()) ? 443 : 80;
  }

  private static void bridgeOtelProperties(Environment environment) {
    Set<String> otelKeys = new LinkedHashSet<>();
    if (environment instanceof AbstractEnvironment) {
      AbstractEnvironment abstractEnv = (AbstractEnvironment) environment;
      abstractEnv
          .getPropertySources()
          .forEach(
              ps -> {
                if (ps instanceof MapPropertySource) {
                  MapPropertySource mps = (MapPropertySource) ps;
                  for (String name : mps.getPropertyNames()) {
                    if (name.startsWith("otel.")) {
                      otelKeys.add(name);
                    }
                  }
                }
              });
    }

    for (String key : otelKeys) {
      bridgeProperty(key, environment.getProperty(key));
    }
  }

  /** Maps {@code alibaba.cloud.sls.*} from application.yml to ALIBABA_CLOUD_* system properties. */
  private static void bridgeSlsCredentials(Environment environment) {
    bridgeProperty(
        "ALIBABA_CLOUD_SLS_ENDPOINT", environment.getProperty("alibaba.cloud.sls.endpoint"));
    bridgeProperty(
        "ALIBABA_CLOUD_ACCESS_KEY_ID", environment.getProperty("alibaba.cloud.sls.access-key-id"));
    bridgeProperty(
        "ALIBABA_CLOUD_ACCESS_KEY_SECRET",
        environment.getProperty("alibaba.cloud.sls.access-key-secret"));
    bridgeProperty(
        "ALIBABA_CLOUD_SLS_REGION", environment.getProperty("alibaba.cloud.sls.region"));
  }

  private static void bridgeProperty(String key, String value) {
    if (value != null && !value.trim().isEmpty() && System.getProperty(key) == null) {
      String envValue = System.getenv(key);
      if (envValue == null || envValue.trim().isEmpty()) {
        System.setProperty(key, value.trim());
      }
    }
  }
}
