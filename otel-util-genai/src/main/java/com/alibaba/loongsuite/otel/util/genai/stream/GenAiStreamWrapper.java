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

package com.alibaba.loongsuite.otel.util.genai.stream;

import java.util.Iterator;

import org.jspecify.annotations.Nullable;

/**
 * Abstract wrapper around a streaming iterator that intercepts each chunk for telemetry processing.
 *
 * <p>When a {@link StreamMetricsCapable} invocation is provided, records time-to-first-chunk and
 * inter-chunk delays for {@code gen_ai.client.operation.time_to_first_chunk} and {@code
 * gen_ai.client.operation.time_per_output_chunk} metrics.
 *
 * @param <T> the type of elements in the stream
 */
public abstract class GenAiStreamWrapper<T> implements Iterator<T>, AutoCloseable {

  private final Iterator<T> delegate;
  private final @Nullable StreamMetricsCapable streamMetrics;
  private final long streamStartNanos;
  private boolean finished = false;
  private int chunkCount = 0;
  private long previousChunkNanos = -1;

  protected GenAiStreamWrapper(Iterator<T> delegate) {
    this(delegate, null);
  }

  protected GenAiStreamWrapper(Iterator<T> delegate, @Nullable StreamMetricsCapable streamMetrics) {
    this.delegate = delegate;
    this.streamMetrics = streamMetrics;
    this.streamStartNanos = System.nanoTime();
  }

  /** Returns the total number of chunks received so far. */
  protected int getChunkCount() {
    return chunkCount;
  }

  protected abstract void processChunk(T chunk);

  protected abstract void onStreamEnd();

  protected abstract void onStreamError(Throwable error);

  @Override
  public boolean hasNext() {
    try {
      boolean hasNext = delegate.hasNext();
      if (!hasNext && !finished) {
        finished = true;
        onStreamEnd();
      }
      return hasNext;
    } catch (Throwable t) {
      if (!finished) {
        finished = true;
        onStreamError(t);
      }
      throw t;
    }
  }

  @Override
  public T next() {
    try {
      T chunk = delegate.next();
      recordChunkTiming();
      processChunk(chunk);
      return chunk;
    } catch (Throwable t) {
      if (!finished) {
        finished = true;
        onStreamError(t);
      }
      throw t;
    }
  }

  private void recordChunkTiming() {
    long now = System.nanoTime();
    chunkCount++;
    if (streamMetrics == null) {
      previousChunkNanos = now;
      return;
    }
    if (chunkCount == 1) {
      streamMetrics.setTimeToFirstChunk((now - streamStartNanos) / 1_000_000_000.0);
      previousChunkNanos = now;
      return;
    }
    if (previousChunkNanos >= 0) {
      streamMetrics.addInterChunkDelay((now - previousChunkNanos) / 1_000_000_000.0);
    }
    previousChunkNanos = now;
  }

  @Override
  public void close() {
    if (!finished) {
      finished = true;
      onStreamEnd();
    }
    if (delegate instanceof AutoCloseable) {
      AutoCloseable ac = (AutoCloseable) delegate;
      try {
        ac.close();
      } catch (Exception ignored) {
        // Best-effort close of delegate
      }
    }
  }
}
