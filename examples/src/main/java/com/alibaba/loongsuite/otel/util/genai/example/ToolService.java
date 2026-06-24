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
import com.alibaba.loongsuite.otel.util.genai.ToolInvocation;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ToolService {

  private final GenAiTelemetryHandler handler;

  private static final Map<String, String> MOCK_WEATHER;
  static {
    MOCK_WEATHER = new HashMap<>();
    MOCK_WEATHER.put("beijing", "{\"city\":\"Beijing\",\"temp\":\"28°C\",\"condition\":\"Sunny\"}");
    MOCK_WEATHER.put("shanghai", "{\"city\":\"Shanghai\",\"temp\":\"32°C\",\"condition\":\"Cloudy\"}");
    MOCK_WEATHER.put("hangzhou", "{\"city\":\"Hangzhou\",\"temp\":\"30°C\",\"condition\":\"Rainy\"}");
  }

  public ToolService(GenAiTelemetryHandler handler) {
    this.handler = handler;
  }

  public ToolResponse executeTool(String toolName, String arguments) {
    try (ToolInvocation inv = handler.tool(toolName, null, "function", toolName)) {
      inv.setArguments(arguments);

      String result = dispatchTool(toolName, arguments);

      inv.setToolResult(result);
      return new ToolResponse(toolName, arguments, result);
    }
  }

  private String dispatchTool(String toolName, String arguments) {
    switch (toolName) {
      case "get_weather": {
        String city = arguments.replaceAll(".*\"city\"\\s*:\\s*\"([^\"]+)\".*", "$1").toLowerCase();
        return MOCK_WEATHER.getOrDefault(city,
            "{\"city\":\"" + city + "\",\"temp\":\"25°C\",\"condition\":\"Unknown\"}");
      }
      case "get_time":
        return "{\"time\":\"" +
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}";
      default:
        return "{\"error\":\"Unknown tool: " + toolName + "\"}";
    }
  }

  public static class ToolResponse {
    private final String tool;
    private final String arguments;
    private final String result;

    public ToolResponse(String tool, String arguments, String result) {
      this.tool = tool;
      this.arguments = arguments;
      this.result = result;
    }

    public String getTool() { return tool; }
    public String getArguments() { return arguments; }
    public String getResult() { return result; }
  }
}
