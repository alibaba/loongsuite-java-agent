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

import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.EVENT_ONLY;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.NO_CONTENT;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.SPAN_AND_EVENT;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.SPAN_ONLY;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.fromString;

import com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class GenAiConfigUtil {

  private static final Logger logger = Logger.getLogger(GenAiConfigUtil.class.getName());

  private GenAiConfigUtil() {}

  /**
   * Returns whether the GenAI experimental semconv mode is enabled.
   *
   * <p>Per OTel spec, the user must set {@code OTEL_SEMCONV_STABILITY_OPT_IN} to include {@code
   * gen_ai_latest_experimental} to opt in to experimental GenAI semantic conventions. When unset,
   * the default mode is stable (non-experimental), and content capturing modes other than simple
   * {@code true/false} are not available.
   */
  public static boolean isExperimentalMode() {
    String value = getProperty(GenAiEnvironmentVariables.OTEL_SEMCONV_STABILITY_OPT_IN);
    if (value == null || value.isEmpty()) {
      return false;
    }
    return value.contains("gen_ai_latest_experimental");
  }

  /**
   * Returns the content capturing mode based on the environment configuration.
   *
   * <p>Behavior depends on whether experimental mode is enabled:
   *
   * <ul>
   *   <li><b>Stable mode</b> (default): only {@code true} is recognized, mapped to {@link
   *       ContentCapturingMode#SPAN_ONLY}. Any other value (including enum names) results in {@code
   *       NO_CONTENT}.
   *   <li><b>Experimental mode</b> ({@code
   *       OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental}): supports all enum values:
   *       {@code no_content}, {@code span_only}, {@code event_only}, {@code span_and_event}. The
   *       value {@code true} maps to {@code SPAN_ONLY} for backward compatibility.
   * </ul>
   */
  public static ContentCapturingMode getContentCapturingMode() {
    String value =
        getProperty(GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT);
    if (value == null || value.isEmpty()) {
      return NO_CONTENT;
    }

    if ("true".equalsIgnoreCase(value)) {
      return SPAN_ONLY;
    }
    if ("false".equalsIgnoreCase(value)) {
      return NO_CONTENT;
    }

    if (!isExperimentalMode()) {
      // Stable mode: enum values like span_only, event_only, span_and_event are treated
      // the same as "true" (SPAN_ONLY) to match Python's should_capture_content() behavior.
      try {
        fromString(value);
        return SPAN_ONLY;
      } catch (IllegalArgumentException e) {
        logger.log(
            Level.WARNING,
            "Unknown value \"{0}\" for {1}. Defaulting to NO_CONTENT.",
            new Object[] {
              value, GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT
            });
        return NO_CONTENT;
      }
    }

    try {
      return fromString(value);
    } catch (IllegalArgumentException e) {
      logger.log(
          Level.WARNING,
          "Unknown value \"{0}\" for {1}. "
              + "Must be one of: true, no_content, span_only, event_only, span_and_event. "
              + "Defaulting to NO_CONTENT.",
          new Object[] {
            value, GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT
          });
      return NO_CONTENT;
    }
  }

  /**
   * Returns whether event emission is enabled.
   *
   * <p>Priority:
   *
   * <ol>
   *   <li>Explicit {@code OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT=true/false}
   *   <li>Inferred from content capturing mode: {@code EVENT_ONLY} or {@code SPAN_AND_EVENT}
   *       implies {@code true}; otherwise {@code false}
   * </ol>
   */
  public static boolean shouldEmitEvent() {
    String value = getProperty(GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT);
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    if (!isExperimentalMode()) {
      return false;
    }
    ContentCapturingMode mode = getContentCapturingMode();
    return mode == EVENT_ONLY || mode == SPAN_AND_EVENT;
  }

  public static boolean shouldCaptureContentOnSpans() {
    ContentCapturingMode mode = getContentCapturingMode();
    return mode == SPAN_ONLY || mode == SPAN_AND_EVENT;
  }

  /**
   * Returns whether message content should be included on inference events.
   *
   * <p>Requires experimental mode and {@link ContentCapturingMode#EVENT_ONLY} or {@link
   * ContentCapturingMode#SPAN_AND_EVENT}.
   */
  public static boolean shouldCaptureContentOnEvents() {
    if (!isExperimentalMode()) {
      return false;
    }
    ContentCapturingMode mode = getContentCapturingMode();
    return mode == EVENT_ONLY || mode == SPAN_AND_EVENT;
  }

  /**
   * Reads a configuration value with the following priority:
   *
   * <ol>
   *   <li>System property (dot-separated, e.g. {@code
   *       otel.instrumentation.genai.capture.message.content})
   *   <li>Environment variable (original key, e.g. {@code
   *       OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT})
   * </ol>
   */
  /** Reads a configuration value from system property or environment variable. */
  public static String getConfigProperty(String envVarName) {
    return getProperty(envVarName);
  }

  /**
   * Returns multimodal upload mode: {@code none}, {@code input}, {@code output}, or {@code both}.
   * Defaults to {@code none}.
   */
  public static MultimodalUploadMode getMultimodalUploadMode() {
    String value =
        getProperty(GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_UPLOAD_MODE);
    return MultimodalUploadMode.fromString(value);
  }

  public static boolean shouldProcessMultimodalInput() {
    MultimodalUploadMode mode = getMultimodalUploadMode();
    return mode == MultimodalUploadMode.INPUT || mode == MultimodalUploadMode.BOTH;
  }

  public static boolean shouldProcessMultimodalOutput() {
    MultimodalUploadMode mode = getMultimodalUploadMode();
    return mode == MultimodalUploadMode.OUTPUT || mode == MultimodalUploadMode.BOTH;
  }

  /**
   * Returns whether PCM audio blobs are converted to WAV before multimodal upload.
   *
   * <p>Defaults to {@code true} so CMS and other consumers can play uploaded ASR input (16 kHz
   * mono PCM). Set to {@code false} to upload raw PCM.
   */
  public static boolean isMultimodalAudioConversionEnabled() {
    String value =
        getProperty(GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_AUDIO_CONVERSION);
    if (value == null || value.isEmpty()) {
      return true;
    }
    return !"false".equalsIgnoreCase(value);
  }

  public static boolean isMultimodalUploadEnabled() {
    if (getMultimodalUploadMode() == MultimodalUploadMode.NONE) {
      return false;
    }
    String basePath =
        getProperty(GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_STORAGE_BASE_PATH);
    return basePath != null && !basePath.isEmpty();
  }

  /**
   * Returns whether LoongSuite extended GenAI semantics are enabled.
   *
   * <p>Controls non-standard attributes like {@code gen_ai.span.kind},
   * {@code gen_ai.*.multimodal_metadata}, and {@code gen_ai.*_ref}.
   * Defaults to {@code true} (LoongSuite distribution has extensions on by default).
   */
  public static boolean isExtendedEnabled() {
    String value =
        getProperty(GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_EXTENDED_ENABLED);
    if (value == null || value.isEmpty()) {
      return true;
    }
    return !"false".equalsIgnoreCase(value);
  }

  private static String getProperty(String envVarName) {
    String sysPropName = envVarName.toLowerCase().replace('_', '.');
    String value = System.getProperty(sysPropName);
    if (value != null && !value.isEmpty()) {
      return value.trim();
    }
    value = System.getenv(envVarName);
    if (value != null && !value.isEmpty()) {
      return value.trim();
    }
    return null;
  }
}
