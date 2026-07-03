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

package com.alibaba.loongsuite.otel.util.genai.example.common;

import com.alibaba.loongsuite.otel.util.genai.stream.StreamMetricsCapable;

/** Records TTFC / inter-chunk delay from streaming callbacks into {@link StreamMetricsCapable} (e.g. InferenceInvocation). */
public final class CallbackStreamMetrics {

  private final long startNanos = System.nanoTime();
  private int chunkCount = 0;
  private long prevChunkNanos = -1;
  private final StreamMetricsCapable genAiInvocation;

  public CallbackStreamMetrics(StreamMetricsCapable genAiInvocation) {
    this.genAiInvocation = genAiInvocation;
  }

  public void onChunk() {
    chunkCount++;
    long now = System.nanoTime();
    if (genAiInvocation != null) {
      if (chunkCount == 1) {
        // First chunk → gen_ai.response.time_to_first_chunk
        genAiInvocation.setTimeToFirstChunk((now - startNanos) / 1e9);
      } else if (prevChunkNanos > 0) {
        // Later chunks → gen_ai.response.inter_chunk_delay (accumulated)
        genAiInvocation.addInterChunkDelay((now - prevChunkNanos) / 1e9);
      }
    }
    prevChunkNanos = now;
  }
}
