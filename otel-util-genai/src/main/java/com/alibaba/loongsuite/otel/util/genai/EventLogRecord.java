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
import io.opentelemetry.api.common.Value;

/**
 * Mutable log event record passed to {@link CompletionHook} before emission.
 *
 * <p>Hooks may stamp additional attributes (e.g. upload reference paths) on the event before it is
 * emitted, matching Python's {@code log_record} parameter on {@code CompletionHook.on_completion}.
 */
public interface EventLogRecord {

  void setAttribute(String key, String value);

  <T> void setAttribute(AttributeKey<T> key, T value);

  void setAttribute(AttributeKey<Value<?>> key, Value<?> value);

  /** Returns the accumulated attributes to emit. */
  Attributes getAttributes();
}
