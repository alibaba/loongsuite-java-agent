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

public final class ServerToolCallPart implements MessagePart {

  private final String name;
  @Nullable private final String id;
  private final Object serverToolCall;

  public ServerToolCallPart(String name, @Nullable String id, Object serverToolCall) {
    this.name = name;
    this.id = id;
    this.serverToolCall = serverToolCall;
  }

  public String name() {
    return name;
  }

  @Nullable
  public String id() {
    return id;
  }

  public Object serverToolCall() {
    return serverToolCall;
  }

  @Override
  public String type() {
    return "server_tool_call";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServerToolCallPart)) return false;
    ServerToolCallPart that = (ServerToolCallPart) o;
    return Objects.equals(name, that.name)
        && Objects.equals(id, that.id)
        && Objects.equals(serverToolCall, that.serverToolCall);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, id, serverToolCall);
  }

  @Override
  public String toString() {
    return "ServerToolCallPart[name="
        + name
        + ", id="
        + id
        + ", serverToolCall="
        + serverToolCall
        + "]";
  }
}
