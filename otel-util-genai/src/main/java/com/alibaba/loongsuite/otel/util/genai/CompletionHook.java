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

/**
 * Hook invoked when a GenAI invocation completes.
 *
 * <p>Implementations are discovered via {@link CompletionHookLoader} or registered explicitly on
 * the {@link GenAiTelemetryHandler.Builder}. Set {@code OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK=upload}
 * to enable {@link UploadCompletionHook}.
 */
@FunctionalInterface
public interface CompletionHook {

  /**
   * Called after a GenAI invocation finishes.
   *
   * <p>For inference invocations with event emission enabled, this is called after the pending
   * event is built but before it is emitted, so hooks can stamp attributes (e.g. upload refs) on
   * both the span and {@link CompletionHookContext#getLogRecord()}.
   *
   * @param context the completion context with access to invocation data
   */
  void onCompletion(CompletionHookContext context);
}
