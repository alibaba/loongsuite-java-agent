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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs multiple {@link CompletionHook} instances in order. */
public final class ChainedCompletionHook implements CompletionHook {

  private static final Logger logger = Logger.getLogger(ChainedCompletionHook.class.getName());
  private final List<CompletionHook> hooks;

  public ChainedCompletionHook(List<CompletionHook> hooks) {
    this.hooks = Collections.unmodifiableList(new ArrayList<>(hooks));
  }

  @Override
  public void onCompletion(CompletionHookContext context) {
    for (CompletionHook hook : hooks) {
      try {
        hook.onCompletion(context);
      } catch (Exception e) {
        logger.log(Level.WARNING, "Completion hook failed: " + hook.getClass().getName(), e);
      }
    }
  }

  public void shutdown() {
    for (CompletionHook hook : hooks) {
      if (hook instanceof MultimodalCompletionHook) {
        ((MultimodalCompletionHook) hook).shutdown();
      }
      if (hook instanceof UploadCompletionHook) {
        ((UploadCompletionHook) hook).shutdown();
      }
    }
  }
}
