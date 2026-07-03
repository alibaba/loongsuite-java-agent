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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** A single multimodal blob upload task. */
public final class MultimodalUploadItem {

  private final String targetUrl;
  private final String contentType;
  private final byte[] data;
  private final Map<String, String> meta;

  public MultimodalUploadItem(
      String targetUrl, String contentType, byte[] data, Map<String, String> meta) {
    this.targetUrl = targetUrl;
    this.contentType = contentType;
    this.data = data;
    this.meta = Collections.unmodifiableMap(meta);
  }

  public String targetUrl() {
    return targetUrl;
  }

  public String contentType() {
    return contentType;
  }

  public byte[] data() {
    return data;
  }

  public Map<String, String> meta() {
    return meta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MultimodalUploadItem)) return false;
    MultimodalUploadItem that = (MultimodalUploadItem) o;
    return Objects.equals(targetUrl, that.targetUrl)
        && Objects.equals(contentType, that.contentType)
        && Objects.equals(meta, that.meta);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetUrl, contentType, meta);
  }
}
