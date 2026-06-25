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

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;

import io.opentelemetry.api.trace.Span;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Provides read-only access to the invocation data available when a {@link CompletionHook} is
 * called.
 */
public interface CompletionHookContext {

  /** Returns the input messages sent to the model. */
  List<InputMessage> getInputs();

  /** Returns the output messages received from the model. */
  List<OutputMessage> getOutputs();

  /** Returns the system instruction parts, if any. */
  List<MessagePart> getSystemInstruction();

  /** Returns the tool definitions provided to the model, or {@code null} if none were set. */
  @Nullable List<ToolDefinition> getToolDefinitions();

  /** Returns the underlying span for the invocation. */
  Span getSpan();

  /**
   * Returns the pending inference event log record, or {@code null} if no event is being emitted.
   *
   * <p>Hooks may stamp additional attributes on the record before it is emitted.
   */
  @Nullable EventLogRecord getLogRecord();
}
