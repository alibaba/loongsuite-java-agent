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

import java.io.IOException;

import org.jspecify.annotations.Nullable;

/**
 * Reads multimodal objects previously uploaded to SLS via {@link SlsUploader}.
 *
 * <p>Uses the SLS Object API ({@code GetObject}), symmetric to {@code PutObject}. Example URI:
 *
 * <pre>{@code sls://liuyu-python-test/liuyu-python-test/20260702/abc123.pcm}</pre>
 */
public final class SlsObjectReader {

  private final SlsMultimodalClient client;

  private SlsObjectReader(SlsMultimodalClient client) {
    this.client = client;
  }

  /** Creates a reader using SLS credentials from environment variables. */
  @Nullable
  public static SlsObjectReader tryCreate() {
    SlsMultimodalClient client = SlsMultimodalClient.tryCreate();
    if (client == null) {
      return null;
    }
    return new SlsObjectReader(client);
  }

  /**
   * Downloads object bytes from a full {@code sls://} URI.
   *
   * @param slsObjectUri full URI including project, logstore, and object name
   * @return object payload and metadata
   * @throws IOException if download fails
   */
  public SlsObjectData getObject(String slsObjectUri) throws IOException {
    try {
      return client.getObject(slsObjectUri);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Failed to get SLS object: " + slsObjectUri, e);
    }
  }
}
