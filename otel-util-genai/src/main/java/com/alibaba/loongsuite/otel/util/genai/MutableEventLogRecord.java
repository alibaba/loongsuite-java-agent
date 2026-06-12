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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.common.Value;

/** Package-private mutable {@link EventLogRecord} backed by an {@link AttributesBuilder}. */
final class MutableEventLogRecord implements EventLogRecord {

  private final AttributesBuilder builder;

  MutableEventLogRecord(Attributes initial) {
    this.builder = initial.toBuilder();
  }

  @Override
  public void setAttribute(String key, String value) {
    builder.put(key, value);
  }

  @Override
  public <T> void setAttribute(AttributeKey<T> key, T value) {
    builder.put(key, value);
  }

  @Override
  public void setAttribute(AttributeKey<Value<?>> key, Value<?> value) {
    builder.put(key, value);
  }

  @Override
  public Attributes getAttributes() {
    return builder.build();
  }
}
