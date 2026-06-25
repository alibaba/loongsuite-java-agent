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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

public final class ToolCallResponsePart implements MessagePart {

  @Nullable private final String id;
  private final Object response;

  public ToolCallResponsePart(@Nullable String id, Object response) {
    this.id = id;
    this.response = response;
  }

  @Nullable
  public String id() {
    return id;
  }

  public Object response() {
    return response;
  }

  @Override
  public String type() {
    return "tool_call_response";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ToolCallResponsePart)) return false;
    ToolCallResponsePart that = (ToolCallResponsePart) o;
    return Objects.equals(id, that.id) && Objects.equals(response, that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, response);
  }

  @Override
  public String toString() {
    return "ToolCallResponsePart[id=" + id + ", response=" + response + "]";
  }
}
