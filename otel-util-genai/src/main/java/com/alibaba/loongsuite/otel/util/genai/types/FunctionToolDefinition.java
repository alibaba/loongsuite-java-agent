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

public final class FunctionToolDefinition implements ToolDefinition {

  private final String name;
  @Nullable private final String description;
  @Nullable private final Object parameters;

  public FunctionToolDefinition(
      String name, @Nullable String description, @Nullable Object parameters) {
    this.name = name;
    this.description = description;
    this.parameters = parameters;
  }

  @Override
  public String name() {
    return name;
  }

  @Nullable
  public String description() {
    return description;
  }

  @Nullable
  public Object parameters() {
    return parameters;
  }

  @Override
  public String type() {
    return "function";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FunctionToolDefinition)) return false;
    FunctionToolDefinition that = (FunctionToolDefinition) o;
    return Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(parameters, that.parameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, description, parameters);
  }

  @Override
  public String toString() {
    return "FunctionToolDefinition[name="
        + name
        + ", description="
        + description
        + ", parameters="
        + parameters
        + "]";
  }
}
