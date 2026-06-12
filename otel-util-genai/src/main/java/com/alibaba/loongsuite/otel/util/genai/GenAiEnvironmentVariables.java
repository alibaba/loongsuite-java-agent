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

public final class GenAiEnvironmentVariables {

  public static final String OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT =
      "OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT";

  public static final String OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT =
      "OTEL_INSTRUMENTATION_GENAI_EMIT_EVENT";

  public static final String OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK =
      "OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK";

  public static final String OTEL_INSTRUMENTATION_GENAI_UPLOAD_BASE_PATH =
      "OTEL_INSTRUMENTATION_GENAI_UPLOAD_BASE_PATH";

  public static final String OTEL_INSTRUMENTATION_GENAI_UPLOAD_FORMAT =
      "OTEL_INSTRUMENTATION_GENAI_UPLOAD_FORMAT";

  public static final String OTEL_INSTRUMENTATION_GENAI_UPLOAD_MAX_QUEUE_SIZE =
      "OTEL_INSTRUMENTATION_GENAI_UPLOAD_MAX_QUEUE_SIZE";

  public static final String OTEL_SEMCONV_STABILITY_OPT_IN = "OTEL_SEMCONV_STABILITY_OPT_IN";

  private GenAiEnvironmentVariables() {}
}
