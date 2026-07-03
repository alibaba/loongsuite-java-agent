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

package com.alibaba.loongsuite.otel.util.genai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Validates that all attribute names used in the implementation match the OTel GenAI Semantic
 * Conventions v1.41.1 registry (aligned with handler schema URL).
 *
 * <p>This test reads {@code semconv/registry.yaml} (the authoritative attribute registry) and scans
 * the Java source files for hardcoded attribute strings, then verifies every attribute used in the
 * code exists in the registry.
 */
class SemconvComplianceTest {

  private static Set<String> registryKeys;
  private static Set<String> codeAttributeKeys;

  // Non-genai attributes that are defined in other OTel semconv registries
  // Attribute keys from other OTel registries, or metric/event names (not attribute keys)
  private static final Set<String> KNOWN_NON_GENAI_KEYS;

  static {
    KNOWN_NON_GENAI_KEYS =
        new HashSet<>(
            Arrays.asList(
                "server.address",
                "server.port",
                "error.type",
                "event.name",
                "exception.type",
                "exception.message",
                "exception.stacktrace",
                "gen_ai.client.operation.duration",
                "gen_ai.client.token.usage",
                "gen_ai.client.operation.time_to_first_chunk",
                "gen_ai.client.operation.time_per_output_chunk",
                "gen_ai.client.inference.operation.details",
                "gen_ai.client.operation.exception",
                "gen_ai.evaluation.result",
                "gen_ai.input.messages_ref",
                "gen_ai.output.messages_ref",
                "gen_ai.system_instructions_ref",
                "gen_ai.tool.definitions_ref",
                "gen_ai.input.multimodal_metadata",
                "gen_ai.output.multimodal_metadata",
                "gen_ai.span.kind"));
  }

  @BeforeAll
  static void loadRegistry() throws IOException {
    registryKeys = parseRegistryKeys();
    codeAttributeKeys = scanCodeAttributeKeys();
  }

  @Test
  void allCodeAttributesMustExistInRegistry() {
    Set<String> unknown = new LinkedHashSet<>();
    for (String key : codeAttributeKeys) {
      if (KNOWN_NON_GENAI_KEYS.contains(key)) {
        continue;
      }
      if (!registryKeys.contains(key)) {
        unknown.add(key);
      }
    }
    if (!unknown.isEmpty()) {
      fail(
          "The following attribute keys used in code are NOT defined in semconv/registry.yaml:\n  "
              + String.join("\n  ", unknown)
              + "\n\nEither fix the key name or add it to KNOWN_NON_GENAI_KEYS if it's from another registry.");
    }
  }

  @Test
  void registryKeysShouldNotBeEmpty() {
    assertTrue(registryKeys.size() > 30, "Expected at least 30 attribute keys from registry.yaml");
  }

  @Test
  void codeKeysShouldNotBeEmpty() {
    assertTrue(codeAttributeKeys.size() > 10, "Expected at least 10 attribute keys in code");
  }

  @Test
  void inferenceSpanRequiredAttributes() {
    Set<String> required =
        new HashSet<>(
            Arrays.asList(
                "gen_ai.operation.name",
                "gen_ai.provider.name",
                "gen_ai.request.model",
                "gen_ai.response.model",
                "gen_ai.response.id",
                "gen_ai.response.finish_reasons",
                "gen_ai.usage.input_tokens",
                "gen_ai.usage.output_tokens",
                "gen_ai.request.temperature",
                "gen_ai.request.top_p",
                "gen_ai.request.max_tokens",
                "gen_ai.request.seed",
                "gen_ai.request.frequency_penalty",
                "gen_ai.request.presence_penalty",
                "gen_ai.request.stop_sequences",
                "gen_ai.request.top_k",
                "gen_ai.request.choice.count",
                "gen_ai.output.type",
                "gen_ai.request.stream",
                "gen_ai.conversation.id",
                "gen_ai.response.time_to_first_chunk",
                "gen_ai.usage.reasoning.output_tokens",
                "gen_ai.usage.cache_creation.input_tokens",
                "gen_ai.usage.cache_read.input_tokens"));

    Set<String> missing = new LinkedHashSet<>();
    for (String key : required) {
      if (!codeAttributeKeys.contains(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      fail(
          "InferenceInvocation is missing these semconv attributes:\n  "
              + String.join("\n  ", missing));
    }
  }

  @Test
  void embeddingSpanRequiredAttributes() {
    Set<String> required =
        new HashSet<>(
            Arrays.asList(
                "gen_ai.operation.name",
                "gen_ai.provider.name",
                "gen_ai.request.model",
                "gen_ai.request.encoding_formats",
                "gen_ai.usage.input_tokens",
                "gen_ai.embeddings.dimension.count",
                "gen_ai.response.model"));

    Set<String> missing = new LinkedHashSet<>();
    for (String key : required) {
      if (!codeAttributeKeys.contains(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      fail(
          "EmbeddingInvocation is missing these semconv attributes:\n  "
              + String.join("\n  ", missing));
    }
  }

  @Test
  void toolSpanRequiredAttributes() {
    Set<String> required =
        new HashSet<>(
            Arrays.asList(
                "gen_ai.operation.name",
                "gen_ai.tool.name",
                "gen_ai.tool.call.id",
                "gen_ai.tool.description",
                "gen_ai.tool.type",
                "gen_ai.tool.call.arguments",
                "gen_ai.tool.call.result"));

    Set<String> missing = new LinkedHashSet<>();
    for (String key : required) {
      if (!codeAttributeKeys.contains(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      fail(
          "ToolInvocation is missing these semconv attributes:\n  " + String.join("\n  ", missing));
    }
  }

  @Test
  void workflowSpanRequiredAttributes() {
    Set<String> required =
        new HashSet<>(
            Arrays.asList(
                "gen_ai.operation.name",
                "gen_ai.workflow.name",
                "gen_ai.input.messages",
                "gen_ai.output.messages"));

    Set<String> missing = new LinkedHashSet<>();
    for (String key : required) {
      if (!codeAttributeKeys.contains(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      fail(
          "WorkflowInvocation is missing these semconv attributes:\n  "
              + String.join("\n  ", missing));
    }
  }

  @Test
  void agentSpanRequiredAttributes() {
    Set<String> required =
        new HashSet<>(
            Arrays.asList(
                "gen_ai.operation.name",
                "gen_ai.provider.name",
                "gen_ai.request.model",
                "gen_ai.agent.id",
                "gen_ai.agent.name",
                "gen_ai.agent.description",
                "gen_ai.agent.version",
                "gen_ai.conversation.id",
                "gen_ai.data_source.id",
                "gen_ai.usage.input_tokens",
                "gen_ai.usage.output_tokens",
                "gen_ai.response.finish_reasons",
                "gen_ai.request.temperature",
                "gen_ai.request.max_tokens",
                "gen_ai.input.messages",
                "gen_ai.output.messages",
                "gen_ai.system_instructions",
                "gen_ai.tool.definitions"));

    Set<String> missing = new LinkedHashSet<>();
    for (String key : required) {
      if (!codeAttributeKeys.contains(key)) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      fail(
          "AgentInvocation is missing these semconv attributes:\n  "
              + String.join("\n  ", missing));
    }
  }

  @Test
  void metricNamesMustMatchSemconv() throws IOException {
    Set<String> specMetricNames = parseMetricNames();
    Set<String> expectedClientMetrics =
        new HashSet<>(
            Arrays.asList(
                "gen_ai.client.operation.duration",
                "gen_ai.client.token.usage",
                "gen_ai.client.operation.time_to_first_chunk",
                "gen_ai.client.operation.time_per_output_chunk"));

    for (String metric : expectedClientMetrics) {
      assertTrue(
          specMetricNames.contains(metric),
          "Expected metric " + metric + " to be defined in semconv/metrics.yaml");
    }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Set<String> parseRegistryKeys() throws IOException {
    Yaml yaml = new Yaml();
    Set<String> keys = new HashSet<>();

    try (InputStream in =
        SemconvComplianceTest.class.getResourceAsStream("/semconv/registry.yaml")) {
      Map<String, Object> doc = yaml.load(in);
      // v1.41+ format: groups[].attributes[].id
      List<Map<String, Object>> groups = (List<Map<String, Object>>) doc.get("groups");
      if (groups != null) {
        for (Map<String, Object> group : groups) {
          List<Map<String, Object>> attrs = (List<Map<String, Object>>) group.get("attributes");
          if (attrs != null) {
            for (Map<String, Object> attr : attrs) {
              String id = (String) attr.get("id");
              if (id != null) {
                keys.add(id);
              }
            }
          }
        }
      }
    }
    return keys;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> parseMetricNames() throws IOException {
    Yaml yaml = new Yaml();
    Set<String> names = new HashSet<>();

    try (InputStream in =
        SemconvComplianceTest.class.getResourceAsStream("/semconv/metrics.yaml")) {
      Map<String, Object> doc = yaml.load(in);
      // v1.41+ format: groups[] with type=metric, metric_name field
      List<Map<String, Object>> groups = (List<Map<String, Object>>) doc.get("groups");
      if (groups != null) {
        for (Map<String, Object> group : groups) {
          String type = (String) group.get("type");
          if ("metric".equals(type)) {
            String name = (String) group.get("metric_name");
            if (name != null) {
              names.add(name);
            }
          }
        }
      }
    }
    return names;
  }

  private static Set<String> scanCodeAttributeKeys() {
    Set<String> keys = new HashSet<>();
    // Match raw string literals (still used in GenAiAttributes.java, GenAiMetricsHelper.java, etc.)
    Pattern stringPattern =
        Pattern.compile("\"(gen_ai\\.[a-z_.]+|server\\.[a-z_.]+|error\\.[a-z_.]+)\"");
    // Match semconv constant usages like GEN_AI_OPERATION_NAME, SERVER_ADDRESS, ERROR_TYPE
    // These correspond to known attribute keys by naming convention (uppercase + underscores)
    Pattern constantPattern =
        Pattern.compile(
            "\\b(GEN_AI_[A-Z_]+|SERVER_ADDRESS|SERVER_PORT|ERROR_TYPE|ERROR_MESSAGE"
                + "|EXCEPTION_TYPE|EXCEPTION_MESSAGE)\\b");

    // Mapping from constant name to semconv key
    Map<String, String> constantToKey = buildConstantToKeyMap();

    Path srcDir = Paths.get("src/main/java/com/alibaba/loongsuite/otel/util/genai");
    try (Stream<Path> files = Files.walk(srcDir)) {
      files
          .filter(p -> p.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                  Matcher stringMatcher = stringPattern.matcher(content);
                  while (stringMatcher.find()) {
                    keys.add(stringMatcher.group(1));
                  }
                  Matcher constMatcher = constantPattern.matcher(content);
                  while (constMatcher.find()) {
                    String mapped = constantToKey.get(constMatcher.group(1));
                    if (mapped != null) {
                      keys.add(mapped);
                    }
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (IOException e) {
      throw new RuntimeException("Failed to scan source files", e);
    }

    return keys;
  }

  private static Map<String, String> buildConstantToKeyMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("GEN_AI_OPERATION_NAME", "gen_ai.operation.name");
    map.put("GEN_AI_PROVIDER_NAME", "gen_ai.provider.name");
    map.put("GEN_AI_REQUEST_MODEL", "gen_ai.request.model");
    map.put("GEN_AI_REQUEST_TEMPERATURE", "gen_ai.request.temperature");
    map.put("GEN_AI_REQUEST_TOP_P", "gen_ai.request.top_p");
    map.put("GEN_AI_REQUEST_FREQUENCY_PENALTY", "gen_ai.request.frequency_penalty");
    map.put("GEN_AI_REQUEST_PRESENCE_PENALTY", "gen_ai.request.presence_penalty");
    map.put("GEN_AI_REQUEST_MAX_TOKENS", "gen_ai.request.max_tokens");
    map.put("GEN_AI_REQUEST_STOP_SEQUENCES", "gen_ai.request.stop_sequences");
    map.put("GEN_AI_REQUEST_SEED", "gen_ai.request.seed");
    map.put("GEN_AI_REQUEST_TOP_K", "gen_ai.request.top_k");
    map.put("GEN_AI_REQUEST_CHOICE_COUNT", "gen_ai.request.choice.count");
    map.put("GEN_AI_REQUEST_STREAM", "gen_ai.request.stream");
    map.put("GEN_AI_REQUEST_ENCODING_FORMATS", "gen_ai.request.encoding_formats");
    map.put("GEN_AI_OUTPUT_TYPE", "gen_ai.output.type");
    map.put("GEN_AI_RESPONSE_MODEL", "gen_ai.response.model");
    map.put("GEN_AI_RESPONSE_ID", "gen_ai.response.id");
    map.put("GEN_AI_RESPONSE_FINISH_REASONS", "gen_ai.response.finish_reasons");
    map.put("GEN_AI_RESPONSE_TIME_TO_FIRST_CHUNK", "gen_ai.response.time_to_first_chunk");
    map.put("GEN_AI_USAGE_INPUT_TOKENS", "gen_ai.usage.input_tokens");
    map.put("GEN_AI_USAGE_OUTPUT_TOKENS", "gen_ai.usage.output_tokens");
    map.put("GEN_AI_USAGE_REASONING_OUTPUT_TOKENS", "gen_ai.usage.reasoning.output_tokens");
    map.put("GEN_AI_USAGE_CACHE_CREATION_INPUT_TOKENS", "gen_ai.usage.cache_creation.input_tokens");
    map.put("GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS", "gen_ai.usage.cache_read.input_tokens");
    map.put("GEN_AI_CONVERSATION_ID", "gen_ai.conversation.id");
    map.put("GEN_AI_DATA_SOURCE_ID", "gen_ai.data_source.id");
    map.put("GEN_AI_EMBEDDINGS_DIMENSION_COUNT", "gen_ai.embeddings.dimension.count");
    map.put("GEN_AI_TOKEN_TYPE", "gen_ai.token.type");
    map.put("GEN_AI_TOOL_NAME", "gen_ai.tool.name");
    map.put("GEN_AI_TOOL_CALL_ID", "gen_ai.tool.call.id");
    map.put("GEN_AI_TOOL_TYPE", "gen_ai.tool.type");
    map.put("GEN_AI_TOOL_DESCRIPTION", "gen_ai.tool.description");
    map.put("GEN_AI_AGENT_NAME", "gen_ai.agent.name");
    map.put("GEN_AI_AGENT_ID", "gen_ai.agent.id");
    map.put("GEN_AI_AGENT_DESCRIPTION", "gen_ai.agent.description");
    map.put("GEN_AI_AGENT_VERSION", "gen_ai.agent.version");
    map.put("GEN_AI_WORKFLOW_NAME", "gen_ai.workflow.name");
    map.put("GEN_AI_RETRIEVAL_QUERY_TEXT", "gen_ai.retrieval.query.text");
    map.put("GEN_AI_EVALUATION_NAME", "gen_ai.evaluation.name");
    map.put("GEN_AI_EVALUATION_SCORE_VALUE", "gen_ai.evaluation.score.value");
    map.put("GEN_AI_EVALUATION_SCORE_LABEL", "gen_ai.evaluation.score.label");
    map.put("GEN_AI_EVALUATION_EXPLANATION", "gen_ai.evaluation.explanation");
    map.put("SERVER_ADDRESS", "server.address");
    map.put("SERVER_PORT", "server.port");
    map.put("ERROR_TYPE", "error.type");
    map.put("EXCEPTION_TYPE", "exception.type");
    map.put("EXCEPTION_MESSAGE", "exception.message");
    map.put("EXCEPTION_STACKTRACE", "exception.stacktrace");
    return map;
  }
}
