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

/**
 * Uploads multimodal binary payloads to external storage.
 *
 * <p>Implementations must be non-blocking on {@link #upload(MultimodalUploadItem)} and handle
 * failures internally without throwing to callers.
 */
public interface MultimodalUploader {

  /**
   * Enqueues an upload task.
   *
   * @return {@code true} if enqueued or skipped as duplicate; {@code false} if queue is full
   */
  boolean upload(MultimodalUploadItem item);

  /** Gracefully shuts down background upload workers. */
  void shutdown(long timeoutMs);
}
