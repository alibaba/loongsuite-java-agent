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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;

public final class InvocationMetricsRecorder {

  private final DoubleHistogram durationHistogram;
  private final LongHistogram tokenHistogram;
  private final DoubleHistogram timeToFirstChunkHistogram;
  private final DoubleHistogram timePerOutputChunkHistogram;

  public InvocationMetricsRecorder(Meter meter) {
    this.durationHistogram = GenAiMetricsHelper.createDurationHistogram(meter);
    this.tokenHistogram = GenAiMetricsHelper.createTokenHistogram(meter);
    this.timeToFirstChunkHistogram = GenAiMetricsHelper.createTimeToFirstChunkHistogram(meter);
    this.timePerOutputChunkHistogram = GenAiMetricsHelper.createTimePerOutputChunkHistogram(meter);
  }

  public void recordDuration(double durationSeconds, Attributes attributes, Context context) {
    durationHistogram.record(durationSeconds, attributes, context);
  }

  public void recordTokenUsage(long tokenCount, Attributes attributes, Context context) {
    tokenHistogram.record(tokenCount, attributes, context);
  }

  public void recordTimeToFirstChunk(double seconds, Attributes attributes, Context context) {
    timeToFirstChunkHistogram.record(seconds, attributes, context);
  }

  public void recordTimePerOutputChunk(double seconds, Attributes attributes, Context context) {
    timePerOutputChunkHistogram.record(seconds, attributes, context);
  }
}
