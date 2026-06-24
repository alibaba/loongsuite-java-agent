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

public final class ServerToolCallResponsePart implements MessagePart {

  @Nullable private final String id;
  private final Object serverToolCallResponse;

  public ServerToolCallResponsePart(@Nullable String id, Object serverToolCallResponse) {
    this.id = id;
    this.serverToolCallResponse = serverToolCallResponse;
  }

  @Nullable
  public String id() {
    return id;
  }

  public Object serverToolCallResponse() {
    return serverToolCallResponse;
  }

  @Override
  public String type() {
    return "server_tool_call_response";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServerToolCallResponsePart)) return false;
    ServerToolCallResponsePart that = (ServerToolCallResponsePart) o;
    return Objects.equals(id, that.id)
        && Objects.equals(serverToolCallResponse, that.serverToolCallResponse);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, serverToolCallResponse);
  }

  @Override
  public String toString() {
    return "ServerToolCallResponsePart[id="
        + id
        + ", serverToolCallResponse="
        + serverToolCallResponse
        + "]";
  }
}
