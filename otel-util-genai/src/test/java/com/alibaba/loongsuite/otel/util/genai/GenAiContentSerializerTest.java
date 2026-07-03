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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.types.BlobPart;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import com.alibaba.loongsuite.otel.util.genai.types.ToolCallRequestPart;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.common.ValueType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GenAiContentSerializerTest {

  @Test
  void testSerializeTextMessages() {
    List<InputMessage> messages =
        Collections.singletonList(
            new InputMessage(
                "user", Collections.singletonList(new TextPart("Hello, how are you?"))));

    String json = GenAiContentSerializer.toJsonString(messages);

    assertNotNull(json);
    assertTrue(json.contains("\"role\""));
    assertTrue(json.contains("\"user\""));
    assertTrue(json.contains("\"content\""));
    assertTrue(json.contains("Hello, how are you?"));
    assertTrue(json.startsWith("["));
    assertTrue(json.endsWith("]"));
  }

  @Test
  void testSerializeMultipleMessages() {
    List<InputMessage> messages =
        Arrays.asList(
            new InputMessage("system", Collections.singletonList(new TextPart("You are helpful."))),
            new InputMessage("user", Collections.singletonList(new TextPart("What is 2+2?"))));

    String json = GenAiContentSerializer.toJsonString(messages);

    assertTrue(json.contains("\"system\""));
    assertTrue(json.contains("\"user\""));
    assertTrue(json.contains("You are helpful."));
    assertTrue(json.contains("What is 2+2?"));
  }

  @Test
  void testSerializeOutputMessages() {
    List<OutputMessage> messages =
        Collections.singletonList(
            new OutputMessage(
                "assistant", Collections.singletonList(new TextPart("The answer is 4.")), "stop"));

    String json = GenAiContentSerializer.toJsonString(messages);

    assertTrue(json.contains("\"assistant\""));
    assertTrue(json.contains("The answer is 4."));
    assertTrue(json.contains("\"stop\""));
    assertTrue(json.contains("\"finish_reason\""));
  }

  @Test
  void testSerializeToolCallRequest() {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("city", "Beijing");
    args.put("units", "celsius");

    ToolCallRequestPart toolCall = new ToolCallRequestPart("get_weather", "call_001", args);
    List<ToolCallRequestPart> parts = Collections.singletonList(toolCall);

    String json = GenAiContentSerializer.toJsonString(parts);

    assertTrue(json.contains("\"get_weather\""));
    assertTrue(json.contains("\"call_001\""));
    assertTrue(json.contains("\"city\""));
    assertTrue(json.contains("\"Beijing\""));
    assertTrue(json.contains("\"units\""));
    assertTrue(json.contains("\"celsius\""));
  }

  @Test
  void testSerializeBlobPartBase64() {
    byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
    String expectedBase64 = Base64.getEncoder().encodeToString(data);

    BlobPart blobPart = new BlobPart("image", "image/png", data);
    List<BlobPart> parts = Collections.singletonList(blobPart);

    String json = GenAiContentSerializer.toJsonString(parts);

    assertTrue(json.contains("\"image\""));
    assertTrue(json.contains("\"image/png\""));
    assertTrue(json.contains(expectedBase64));
  }

  @Test
  void testToMapList() {
    List<InputMessage> messages =
        Collections.singletonList(
            new InputMessage("user", Collections.singletonList(new TextPart("Hello"))));

    List<Map<String, Object>> mapList = GenAiContentSerializer.toMapList(messages);

    assertEquals(1, mapList.size());
    Map<String, Object> map = mapList.get(0);
    assertEquals("user", map.get("role"));
    assertNotNull(map.get("parts"));
    assertTrue(map.get("parts") instanceof List);

    @SuppressWarnings("unchecked")
    List<Object> parts = (List<Object>) map.get("parts");
    assertEquals(1, parts.size());
    assertTrue(parts.get(0) instanceof Map);

    @SuppressWarnings("unchecked")
    Map<String, Object> partMap = (Map<String, Object>) parts.get(0);
    assertEquals("Hello", partMap.get("content"));
  }

  @Test
  void testToMapListMultiple() {
    List<InputMessage> messages =
        Arrays.asList(
            new InputMessage("user", Collections.singletonList(new TextPart("First"))),
            new InputMessage("assistant", Collections.singletonList(new TextPart("Second"))));

    List<Map<String, Object>> mapList = GenAiContentSerializer.toMapList(messages);
    assertEquals(2, mapList.size());
    assertEquals("user", mapList.get(0).get("role"));
    assertEquals("assistant", mapList.get(1).get("role"));
  }

  @Test
  void testEmptyList() {
    String json = GenAiContentSerializer.toJsonString(Collections.emptyList());
    assertEquals("[]", json);
  }

  @Test
  void testSerializeSpecialCharacters() {
    List<InputMessage> messages =
        Collections.singletonList(
            new InputMessage(
                "user",
                Collections.singletonList(new TextPart("He said \"hello\" and\\then\nnewline"))));

    String json = GenAiContentSerializer.toJsonString(messages);

    assertTrue(json.contains("\\\"hello\\\""));
    assertTrue(json.contains("\\\\then"));
    assertTrue(json.contains("\\n"));
  }

  @Test
  void testSerializeTextPart() {
    List<TextPart> parts = Collections.singletonList(new TextPart("plain text"));

    String json = GenAiContentSerializer.toJsonString(parts);

    assertTrue(json.contains("\"content\""));
    assertTrue(json.contains("\"plain text\""));
  }

  @Test
  void testToMapListEmpty() {
    List<Map<String, Object>> result = GenAiContentSerializer.toMapList(Collections.emptyList());
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testToValueStructuredMessages() {
    List<InputMessage> messages =
        Collections.singletonList(
            new InputMessage("user", Collections.singletonList(new TextPart("Hello"))));

    Value<?> value = GenAiContentSerializer.toValue(messages);

    assertEquals(ValueType.ARRAY, value.getType());
    @SuppressWarnings("unchecked")
    List<Value<?>> array = (List<Value<?>>) value.getValue();
    assertEquals(1, array.size());
    assertEquals(ValueType.KEY_VALUE_LIST, array.get(0).getType());
  }

  @Test
  void testToValueString() {
    Value<?> value = GenAiContentSerializer.toValue("{\"city\":\"Paris\"}");
    assertEquals(ValueType.STRING, value.getType());
    assertEquals("{\"city\":\"Paris\"}", value.getValue());
  }
}
