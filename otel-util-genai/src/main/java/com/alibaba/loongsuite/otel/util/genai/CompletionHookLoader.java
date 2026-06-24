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

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a {@link CompletionHook} implementation using the Java {@link ServiceLoader} mechanism.
 *
 * <p>The hook class name is specified via the environment variable {@link
 * GenAiEnvironmentVariables#OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK}. If the variable is not
 * set, a no-op hook is returned.
 */
public final class CompletionHookLoader {

  private static final Logger logger = Logger.getLogger(CompletionHookLoader.class.getName());

  private CompletionHookLoader() {}

  /**
   * Loads the configured {@link CompletionHook}, falling back to a no-op if none is configured or
   * the specified class cannot be found.
   */
  public static CompletionHook load() {
    String hookName =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK);
    if (hookName == null || hookName.isEmpty()) {
      return NoOpCompletionHook.INSTANCE;
    }

    if ("upload".equals(hookName)) {
      CompletionHook uploadHook = UploadCompletionHook.tryCreate();
      if (!(uploadHook instanceof NoOpCompletionHook)) {
        return uploadHook;
      }
    }

    // Try ServiceLoader first
    ServiceLoader<CompletionHook> loader = ServiceLoader.load(CompletionHook.class);
    for (CompletionHook hook : loader) {
      if (hook.getClass().getName().equals(hookName)) {
        logger.fine(() -> "Using CompletionHook " + hookName);
        return hook;
      }
    }

    // Fallback: try Class.forName with reflection
    try {
      Class<?> clazz = Class.forName(hookName);
      Object instance = clazz.getDeclaredConstructor().newInstance();
      if (instance instanceof CompletionHook) {
        CompletionHook ch = (CompletionHook) instance;
        logger.fine(() -> "Loaded CompletionHook via reflection: " + hookName);
        return ch;
      }
      logger.warning(
          () -> "Class " + hookName + " does not implement CompletionHook, using no-op fallback");
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to load CompletionHook: " + hookName, e);
    }

    return NoOpCompletionHook.INSTANCE;
  }
}
