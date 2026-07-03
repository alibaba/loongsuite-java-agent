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
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;

import io.opentelemetry.api.trace.Span;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

/**
 * Completion hook that uploads multimodal blobs and replaces them with URI references before span
 * content attributes are applied.
 */
public final class MultimodalCompletionHook implements CompletionHook {

  private static final Logger logger = Logger.getLogger(MultimodalCompletionHook.class.getName());

  private final MultimodalPreUploader preUploader;
  private final MultimodalUploader uploader;

  MultimodalCompletionHook(MultimodalPreUploader preUploader, MultimodalUploader uploader) {
    this.preUploader = preUploader;
    this.uploader = uploader;
  }

  @Nullable
  public static MultimodalCompletionHook tryCreate() {
    if (!GenAiConfigUtil.isMultimodalUploadEnabled()) {
      return null;
    }
    if (!GenAiConfigUtil.isExperimentalMode()) {
      return null;
    }
    if (!GenAiConfigUtil.shouldCaptureContentOnSpans()
        && !GenAiConfigUtil.shouldCaptureContentOnEvents()) {
      return null;
    }
    String basePath =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH);
    if (basePath == null || basePath.isEmpty()) {
      logger.warning(
          GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH
              + " is required for multimodal upload");
      return null;
    }
    MultimodalUploader uploader = MultimodalUploaderLoader.load();
    if (uploader == null) {
      return null;
    }
    return new MultimodalCompletionHook(new MultimodalPreUploader(basePath), uploader);
  }

  @Override
  public void onCompletion(CompletionHookContext context) {
    if (!(context instanceof MutableCompletionHookContext)) {
      return;
    }
    MutableCompletionHookContext mutableContext = (MutableCompletionHookContext) context;
    MessageContentCapable messageHolder = mutableContext.getMessageContentCapable();
    if (messageHolder == null) {
      return;
    }

    Span span = context.getSpan();
    MultimodalPreUploader.ProcessResult result =
        preUploader.process(
            span.getSpanContext(),
            mutableContext.getStartTimeEpochMillis(),
            messageHolder.getInputMessages(),
            messageHolder.getOutputMessages());

    messageHolder.setInputMessages(result.inputMessages());
    messageHolder.setOutputMessages(result.outputMessages());
    mutableContext.replaceInputs(result.inputMessages());
    mutableContext.replaceOutputs(result.outputMessages());

    for (MultimodalUploadItem item : result.uploadItems()) {
      uploader.upload(item);
    }

    String inputMetadata = MultimodalPreUploader.extractInputMetadataJson(result.inputMessages());
    if (inputMetadata != null && GenAiConfigUtil.isExtendedEnabled()) {
      span.setAttribute(GenAiAttributes.GEN_AI_INPUT_MULTIMODAL_METADATA, inputMetadata);
      EventLogRecord logRecord = context.getLogRecord();
      if (logRecord != null) {
        logRecord.setAttribute(GenAiAttributes.GEN_AI_INPUT_MULTIMODAL_METADATA, inputMetadata);
      }
    }
    String outputMetadata = MultimodalPreUploader.extractOutputMetadataJson(result.outputMessages());
    if (outputMetadata != null && GenAiConfigUtil.isExtendedEnabled()) {
      span.setAttribute(GenAiAttributes.GEN_AI_OUTPUT_MULTIMODAL_METADATA, outputMetadata);
      EventLogRecord logRecord = context.getLogRecord();
      if (logRecord != null) {
        logRecord.setAttribute(GenAiAttributes.GEN_AI_OUTPUT_MULTIMODAL_METADATA, outputMetadata);
      }
    }
  }

  void shutdown() {
    uploader.shutdown(5000);
  }
}
