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

package com.alibaba.loongsuite.otel.util.genai.example.asr.service;

import com.alibaba.loongsuite.otel.util.genai.GenAiTelemetryHandler;
import com.alibaba.loongsuite.otel.util.genai.ToolInvocation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Mock weather tool: {@code handler.tool("get_weather")} → execute_tool get_weather span. */
@Service
public class WeatherToolService {

  private static final Map<String, String> MOCK_WEATHER_CN = new HashMap<>();

  static {
    MOCK_WEATHER_CN.put("hangzhou", "杭州今天多云，气温 26°C，东南风 2 级，适合出行。");
    MOCK_WEATHER_CN.put("beijing", "北京今天晴，气温 28°C，空气质量良好。");
    MOCK_WEATHER_CN.put("shanghai", "上海今天阴，气温 24°C，可能有短时小雨。");
  }

  private final GenAiTelemetryHandler handler;

  public WeatherToolService(GenAiTelemetryHandler handler) {
    this.handler = handler;
  }

  public String getWeather(String cityKey) {
    String summary =
        MOCK_WEATHER_CN.getOrDefault(
            cityKey, cityKey + "今天晴，气温 25°C。");
    String args = "{\"city\":\"" + cityKey + "\"}";
    String result = "{\"city\":\"" + cityKey + "\",\"summary\":\"" + summary + "\"}";
    try (ToolInvocation tool = handler.tool("get_weather")) {
      tool.setArguments(args);   // → gen_ai.tool.call.arguments
      tool.setToolResult(result); // → gen_ai.tool.call.result
    }
    return summary;
  }
}
