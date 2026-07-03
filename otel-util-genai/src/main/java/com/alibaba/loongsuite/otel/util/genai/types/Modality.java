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

package com.alibaba.loongsuite.otel.util.genai.types;

import org.jspecify.annotations.Nullable;

/**
 * GenAI multimodal modality values, aligned with Python {@code Modality = Literal["image",
 * "video", "audio"]}.
 */
public final class Modality {

  public static final String IMAGE = "image";
  public static final String VIDEO = "video";
  public static final String AUDIO = "audio";

  private Modality() {}

  /**
   * Infers modality from a MIME type prefix (e.g. {@code audio/wav} → {@code audio}).
   *
   * @return {@code image}, {@code audio}, {@code video}, or {@code null} if unknown
   */
  @Nullable
  public static String fromMimeType(@Nullable String mimeType) {
    if (mimeType == null || mimeType.isEmpty()) {
      return null;
    }
    if (mimeType.startsWith("image/")) {
      return IMAGE;
    }
    if (mimeType.startsWith("audio/")) {
      return AUDIO;
    }
    if (mimeType.startsWith("video/")) {
      return VIDEO;
    }
    return null;
  }

  /**
   * Returns {@code modality} when set, otherwise infers from {@code mimeType}.
   *
   * @return resolved modality, or {@code null} if neither is available
   */
  @Nullable
  public static String resolve(@Nullable String modality, @Nullable String mimeType) {
    if (modality != null && !modality.isEmpty()) {
      return modality;
    }
    return fromMimeType(mimeType);
  }
}
