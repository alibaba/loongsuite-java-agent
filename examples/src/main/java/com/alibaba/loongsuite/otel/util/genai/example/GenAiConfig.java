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

package com.alibaba.loongsuite.otel.util.genai.example;

import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.opentelemetry.api.OpenTelemetry;
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

@Configuration
public class GenAiConfig {

  /**
   * Initializes OpenTelemetry via SDK autoconfigure.
   *
   * <p>Properties from {@code application.yml} and {@code -D} flags are bridged into system
   * properties before initialization so both {@code otel.*} SDK config and {@code
   * otel.instrumentation.genai.*} content-capture settings are picked up.
   */
  @Bean
  public OpenTelemetry openTelemetry(Environment environment) {
    bridgeOtelProperties(environment);
    return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
  }

  @Bean
  public GenAiTelemetryHandler genAiTelemetryHandler(OpenTelemetry openTelemetry) {
    return GenAiTelemetryHandler.create(openTelemetry);
  }

  @Bean
  public OpenAIClient openAIClient(
      @Value("${genai.api-key}") String apiKey,
      @Value("${genai.base-url}") String baseUrl) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalStateException(
          "genai.api-key is not set. Provide it via:\n"
              + "  -Dgenai.api-key=sk-xxx\n"
              + "  or export GENAI_API_KEY=sk-xxx");
    }
    return OpenAIOkHttpClient.builder().apiKey(apiKey).baseUrl(baseUrl).build();
  }

  @Bean
  public String genAiServerAddress(@Value("${genai.base-url}") String baseUrl) {
    return URI.create(baseUrl).getHost();
  }

  @Bean
  public Integer genAiServerPort(@Value("${genai.base-url}") String baseUrl) {
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
      String resolved = environment.getProperty(key);
      if (resolved != null && !resolved.trim().isEmpty() && System.getProperty(key) == null) {
        System.setProperty(key, resolved);
      }
    }
  }
}
