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

package com.alibaba.loongsuite.otel.util.genai.types;

public enum FinishReason {
  STOP("stop"),
  LENGTH("length"),
  TOOL_CALLS("tool_calls"),
  CONTENT_FILTER("content_filter"),
  ERROR("error");

  private final String value;

  FinishReason(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static FinishReason fromString(String value) {
    for (FinishReason reason : values()) {
      if (reason.value.equals(value)) {
        return reason;
      }
    }
    throw new IllegalArgumentException("Unknown finish reason: " + value);
  }
}
