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

import java.util.List;
import java.util.Objects;

public final class OutputMessage {

  private final String role;
  private final List<MessagePart> parts;
  private final String finishReason;

  public OutputMessage(String role, List<MessagePart> parts, String finishReason) {
    this.role = role;
    this.parts = parts;
    this.finishReason = finishReason;
  }

  public String role() {
    return role;
  }

  public List<MessagePart> parts() {
    return parts;
  }

  public String finishReason() {
    return finishReason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OutputMessage)) return false;
    OutputMessage that = (OutputMessage) o;
    return Objects.equals(role, that.role)
        && Objects.equals(parts, that.parts)
        && Objects.equals(finishReason, that.finishReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, parts, finishReason);
  }

  @Override
  public String toString() {
    return "OutputMessage[role="
        + role
        + ", parts="
        + parts
        + ", finishReason="
        + finishReason
        + "]";
  }
}
