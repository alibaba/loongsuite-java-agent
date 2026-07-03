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

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

public final class BlobPart implements MessagePart {

  private final @Nullable String modality;
  @Nullable private final String mimeType;
  private final byte[] content;

  public BlobPart(String modality, @Nullable String mimeType, byte[] content) {
    this.modality = Modality.resolve(modality, mimeType);
    this.mimeType = mimeType;
    this.content = content;
  }

  /** Creates a blob part; modality is inferred from {@code mimeType} when omitted. */
  public BlobPart(@Nullable String mimeType, byte[] content) {
    this(null, mimeType, content);
  }

  @Nullable
  public String modality() {
    return modality;
  }

  @Nullable
  public String mimeType() {
    return mimeType;
  }

  public byte[] content() {
    return content;
  }

  @Override
  public String type() {
    return "blob";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BlobPart)) return false;
    BlobPart that = (BlobPart) o;
    return Objects.equals(modality, that.modality)
        && Objects.equals(mimeType, that.mimeType)
        && Arrays.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(modality, mimeType);
    result = 31 * result + Arrays.hashCode(content);
    return result;
  }

  @Override
  public String toString() {
    return "BlobPart[modality="
        + modality
        + ", mimeType="
        + mimeType
        + ", content="
        + Arrays.toString(content)
        + "]";
  }
}
