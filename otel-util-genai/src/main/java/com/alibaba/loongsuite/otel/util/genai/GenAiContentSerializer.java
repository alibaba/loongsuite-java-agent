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

import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.common.ValueType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GenAiContentSerializer {

  private static final String TYPES_PACKAGE = "com.alibaba.loongsuite.otel.util.genai.types";

  private GenAiContentSerializer() {}

  /**
   * Converts a list of message/document objects to a structured {@link Value} for semconv attrs.
   */
  public static Value<?> toValue(List<?> items) {
    List<Value<?>> values = new ArrayList<>();
    for (Object item : items) {
      if (isDataObject(item)) {
        values.add(objectToValue(dataObjectToMap(item)));
      } else if (item instanceof Map) {
        values.add(objectToValue(item));
      }
    }
    return Value.of(values);
  }

  /** Wraps a string payload as a {@link Value} (e.g. tool arguments/result). */
  public static Value<?> toValue(String value) {
    return Value.of(value);
  }

  public static String toJsonString(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder();
    serializeValue(sb, map);
    return sb.toString();
  }

  public static String toJsonString(List<?> items) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      serializeValue(sb, items.get(i));
    }
    sb.append(']');
    return sb.toString();
  }

  public static List<Map<String, Object>> toMapList(List<?> items) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : items) {
      if (isDataObject(item)) {
        result.add(dataObjectToMap(item));
      } else if (item instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) item;
        result.add(map);
      }
    }
    return result;
  }

  private static void serializeValue(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String) {
      sb.append('"');
      escapeJsonString(sb, (String) value);
      sb.append('"');
    } else if (value instanceof Number) {
      sb.append(value);
    } else if (value instanceof Boolean) {
      sb.append(value);
    } else if (value instanceof byte[]) {
      sb.append('"');
      sb.append(Base64.getEncoder().encodeToString((byte[]) value));
      sb.append('"');
    } else if (value instanceof List) {
      List<?> list = (List<?>) value;
      sb.append('[');
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        serializeValue(sb, list.get(i));
      }
      sb.append(']');
    } else if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      sb.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        sb.append('"');
        escapeJsonString(sb, String.valueOf(entry.getKey()));
        sb.append('"');
        sb.append(':');
        serializeValue(sb, entry.getValue());
      }
      sb.append('}');
    } else if (isDataObject(value)) {
      serializeDataObject(sb, value);
    } else {
      sb.append('"');
      escapeJsonString(sb, String.valueOf(value));
      sb.append('"');
    }
  }

  private static void serializeDataObject(StringBuilder sb, Object obj) {
    sb.append('{');
    boolean first = true;
    boolean typeInjected = false;
    if (obj instanceof MessagePart) {
      sb.append("\"type\":");
      serializeValue(sb, ((MessagePart) obj).type());
      first = false;
      typeInjected = true;
    } else if (obj instanceof ToolDefinition) {
      sb.append("\"type\":");
      serializeValue(sb, ((ToolDefinition) obj).type());
      first = false;
      typeInjected = true;
    }
    Field[] fields = obj.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (typeInjected && "type".equals(field.getName())) {
        continue;
      }
      Object fieldValue;
      try {
        field.setAccessible(true);
        fieldValue = field.get(obj);
      } catch (Exception e) {
        continue;
      }
      if (fieldValue == null) {
        continue;
      }
      if (!first) {
        sb.append(',');
      }
      first = false;
      sb.append('"');
      escapeJsonString(sb, camelToSnake(field.getName()));
      sb.append('"');
      sb.append(':');
      serializeValue(sb, fieldValue);
    }
    sb.append('}');
  }

  @SuppressWarnings("unchecked")
  private static Value<?> objectToValue(Object obj) {
    if (obj == null) {
      return Value.empty();
    }
    if (obj instanceof String) {
      return Value.of((String) obj);
    }
    if (obj instanceof Boolean) {
      return Value.of((Boolean) obj);
    }
    if (obj instanceof Integer || obj instanceof Long) {
      return Value.of(((Number) obj).longValue());
    }
    if (obj instanceof Float || obj instanceof Double) {
      return Value.of(((Number) obj).doubleValue());
    }
    if (obj instanceof byte[]) {
      return Value.of((byte[]) obj);
    }
    if (obj instanceof List) {
      List<Value<?>> values = new ArrayList<>();
      for (Object item : (List<?>) obj) {
        Value<?> converted = objectToValue(item);
        if (converted.getType() != ValueType.EMPTY) {
          values.add(converted);
        }
      }
      return Value.of(values);
    }
    if (obj instanceof Map) {
      Map<String, Value<?>> converted = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
        converted.put(String.valueOf(entry.getKey()), objectToValue(entry.getValue()));
      }
      return Value.of(converted);
    }
    return Value.of(String.valueOf(obj));
  }

  static Map<String, Object> dataObjectToMap(Object obj) {
    Map<String, Object> map = new LinkedHashMap<>();
    boolean typeInjected = injectInterfaceType(obj, map);
    Field[] fields = obj.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (typeInjected && "type".equals(field.getName())) {
        continue;
      }
      Object fieldValue;
      try {
        field.setAccessible(true);
        fieldValue = field.get(obj);
      } catch (Exception e) {
        continue;
      }
      if (fieldValue == null) {
        continue;
      }
      String key = camelToSnake(field.getName());
      if (isDataObject(fieldValue)) {
        map.put(key, dataObjectToMap(fieldValue));
      } else if (fieldValue instanceof List) {
        List<?> list = (List<?>) fieldValue;
        List<Object> converted = new ArrayList<>();
        for (Object item : list) {
          if (isDataObject(item)) {
            converted.add(dataObjectToMap(item));
          } else {
            converted.add(item);
          }
        }
        map.put(key, converted);
      } else {
        map.put(key, fieldValue);
      }
    }
    return map;
  }

  /**
   * Injects the {@code type} field from interface methods ({@link MessagePart#type()}, {@link
   * ToolDefinition#type()}) which are not instance fields but should appear in serialized output to
   * match Python's {@code dataclasses.asdict()} behavior.
   *
   * @return {@code true} if a type field was injected
   */
  private static boolean injectInterfaceType(Object obj, Map<String, Object> map) {
    if (obj instanceof MessagePart) {
      map.put("type", ((MessagePart) obj).type());
      return true;
    } else if (obj instanceof ToolDefinition) {
      map.put("type", ((ToolDefinition) obj).type());
      return true;
    }
    return false;
  }

  private static boolean isDataObject(Object obj) {
    if (obj == null) {
      return false;
    }
    Package pkg = obj.getClass().getPackage();
    return pkg != null && TYPES_PACKAGE.equals(pkg.getName());
  }

  static String camelToSnake(String name) {
    StringBuilder sb = new StringBuilder(name.length() + 4);
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (Character.isUpperCase(ch)) {
        if (i > 0) {
          sb.append('_');
        }
        sb.append(Character.toLowerCase(ch));
      } else {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private static void escapeJsonString(StringBuilder sb, String str) {
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      switch (ch) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (ch < 0x20) {
            sb.append(String.format("\\u%04x", (int) ch));
          } else {
            sb.append(ch);
          }
          break;
      }
    }
  }
}
