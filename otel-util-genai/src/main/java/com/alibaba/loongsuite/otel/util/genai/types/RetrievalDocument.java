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

/** Represents a document returned by a GenAI retrieval operation. */
public final class RetrievalDocument {

  @Nullable private final String id;
  @Nullable private final Double score;
  @Nullable private final String content;

  public RetrievalDocument(@Nullable String id, @Nullable Double score, @Nullable String content) {
    this.id = id;
    this.score = score;
    this.content = content;
  }

  @Nullable
  public String id() {
    return id;
  }

  @Nullable
  public Double score() {
    return score;
  }

  @Nullable
  public String content() {
    return content;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RetrievalDocument)) return false;
    RetrievalDocument that = (RetrievalDocument) o;
    return Objects.equals(id, that.id)
        && Objects.equals(score, that.score)
        && Objects.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, score, content);
  }

  @Override
  public String toString() {
    return "RetrievalDocument[id=" + id + ", score=" + score + ", content=" + content + "]";
  }
}
