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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

public final class UriPart implements MessagePart {

  private final @Nullable String modality;
  @Nullable private final String mimeType;
  private final String uri;

  public UriPart(String modality, @Nullable String mimeType, String uri) {
    this.modality = Modality.resolve(modality, mimeType);
    this.mimeType = mimeType;
    this.uri = uri;
  }

  /** Creates a URI part; modality is inferred from {@code mimeType} when omitted. */
  public UriPart(@Nullable String mimeType, String uri) {
    this(null, mimeType, uri);
  }

  @Nullable
  public String modality() {
    return modality;
  }

  @Nullable
  public String mimeType() {
    return mimeType;
  }

  public String uri() {
    return uri;
  }

  @Override
  public String type() {
    return "uri";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UriPart)) return false;
    UriPart that = (UriPart) o;
    return Objects.equals(modality, that.modality)
        && Objects.equals(mimeType, that.mimeType)
        && Objects.equals(uri, that.uri);
  }

  @Override
  public int hashCode() {
    return Objects.hash(modality, mimeType, uri);
  }

  @Override
  public String toString() {
    return "UriPart[modality=" + modality + ", mimeType=" + mimeType + ", uri=" + uri + "]";
  }
}
