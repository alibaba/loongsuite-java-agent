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
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@link CompletionHook} implementations using the Java {@link ServiceLoader} mechanism.
 *
 * <p>When multimodal upload is enabled, {@link MultimodalCompletionHook} is prepended before other
 * hooks (such as {@link UploadCompletionHook}) so blob parts are replaced with URI references
 * before message content is serialized to spans and events.
 */
public final class CompletionHookLoader {

  private static final Logger logger = Logger.getLogger(CompletionHookLoader.class.getName());

  private CompletionHookLoader() {}

  /** Loads the configured {@link CompletionHook} chain, falling back to a no-op if none apply. */
  public static CompletionHook load() {
    List<CompletionHook> chain = new ArrayList<>();

    MultimodalCompletionHook multimodalHook = MultimodalCompletionHook.tryCreate();
    if (multimodalHook != null) {
      chain.add(multimodalHook);
    }

    String hookName =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK);

    if ("upload".equals(hookName)) {
      CompletionHook uploadHook = UploadCompletionHook.tryCreate();
      if (!(uploadHook instanceof NoOpCompletionHook)) {
        chain.add(uploadHook);
      }
    } else if (hookName != null && !hookName.isEmpty()) {
      CompletionHook customHook = loadCustomHook(hookName);
      if (customHook != null) {
        chain.add(customHook);
      }
    }

    if (chain.isEmpty()) {
      return NoOpCompletionHook.INSTANCE;
    }
    if (chain.size() == 1) {
      return chain.get(0);
    }
    return new ChainedCompletionHook(chain);
  }

  private static CompletionHook loadCustomHook(String hookName) {
    ServiceLoader<CompletionHook> loader = ServiceLoader.load(CompletionHook.class);
    for (CompletionHook hook : loader) {
      if (hook.getClass().getName().equals(hookName)) {
        logger.fine("Using CompletionHook " + hookName);
        return hook;
      }
    }

    try {
      Class<?> clazz = Class.forName(hookName);
      Object instance = clazz.getDeclaredConstructor().newInstance();
      if (instance instanceof CompletionHook) {
        CompletionHook ch = (CompletionHook) instance;
        logger.fine("Loaded CompletionHook via reflection: " + hookName);
        return ch;
      }
      logger.warning(
          "Class " + hookName + " does not implement CompletionHook, using no-op fallback");
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to load CompletionHook: " + hookName, e);
    }
    return null;
  }
}
