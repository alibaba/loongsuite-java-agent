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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a {@link CompletionHook} to catch and log any exceptions thrown during {@link
 * #onCompletion(CompletionHookContext)}, preventing hook errors from affecting the instrumentation
 * lifecycle.
 */
final class SafeCompletionHook implements CompletionHook {

  private static final Logger logger = Logger.getLogger(SafeCompletionHook.class.getName());

  private final CompletionHook delegate;

  SafeCompletionHook(CompletionHook delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onCompletion(CompletionHookContext context) {
    try {
      delegate.onCompletion(context);
    } catch (Throwable t) {
      logger.log(Level.WARNING, "CompletionHook threw exception", t);
    }
  }
}
