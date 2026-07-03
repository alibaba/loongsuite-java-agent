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

import org.jspecify.annotations.Nullable;

/** Loads {@link MultimodalUploader} implementations from configuration. */
public final class MultimodalUploaderLoader {

  private static final Logger logger = Logger.getLogger(MultimodalUploaderLoader.class.getName());

  private MultimodalUploaderLoader() {}

  @Nullable
  public static MultimodalUploader load() {
    if (!GenAiConfigUtil.isMultimodalUploadEnabled()) {
      return null;
    }
    String basePath =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH);
    if (basePath == null || basePath.isEmpty()) {
      return null;
    }
    String uploaderName =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOADER);
    if (uploaderName == null || uploaderName.isEmpty()) {
      uploaderName = inferUploaderFromBasePath(basePath);
    }
    if ("sls".equalsIgnoreCase(uploaderName)) {
      SlsUploader slsUploader = SlsUploader.tryCreate(basePath);
      if (slsUploader != null) {
        return slsUploader;
      }
      logger.warning("Falling back to local multimodal uploader after SLS init failure");
    }
    return LocalFileUploader.tryCreate(basePath);
  }

  private static String inferUploaderFromBasePath(String basePath) {
    if (basePath.startsWith("sls://")) {
      return "sls";
    }
    return "local";
  }
}
