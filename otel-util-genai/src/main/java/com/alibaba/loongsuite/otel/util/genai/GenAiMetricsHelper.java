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

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GenAiMetricsHelper {

  private static final Logger logger = Logger.getLogger(GenAiMetricsHelper.class.getName());

  private static final List<Double> DURATION_BUCKETS =
      Arrays.asList(
          0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96,
          81.92);

  private static final List<Long> TOKEN_BUCKETS =
      Arrays.asList(
          1L, 4L, 16L, 64L, 256L, 1024L, 4096L, 16384L, 65536L, 262144L, 1048576L, 4194304L,
          16777216L, 67108864L);

  private GenAiMetricsHelper() {}

  public static DoubleHistogram createDurationHistogram(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("gen_ai.client.operation.duration")
            .setDescription("Duration of GenAI client operation")
            .setUnit("s");
    try {
      ExtendedDoubleHistogramBuilder extendedBuilder = (ExtendedDoubleHistogramBuilder) builder;
      extendedBuilder.setExplicitBucketBoundariesAdvice(DURATION_BUCKETS);
    } catch (ClassCastException e) {
      logger.log(
          Level.FINE,
          "ExtendedDoubleHistogramBuilder not available, building without advisory buckets",
          e);
    }
    return builder.build();
  }

  public static DoubleHistogram createTimeToFirstChunkHistogram(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("gen_ai.client.operation.time_to_first_chunk")
            .setDescription(
                "Time to receive the first chunk, measured from when the client issues"
                    + " the generation request to when the first chunk is received")
            .setUnit("s");
    try {
      ExtendedDoubleHistogramBuilder extendedBuilder = (ExtendedDoubleHistogramBuilder) builder;
      extendedBuilder.setExplicitBucketBoundariesAdvice(DURATION_BUCKETS);
    } catch (ClassCastException e) {
      logger.log(Level.FINE, "ExtendedDoubleHistogramBuilder not available", e);
    }
    return builder.build();
  }

  public static DoubleHistogram createTimePerOutputChunkHistogram(Meter meter) {
    DoubleHistogramBuilder builder =
        meter
            .histogramBuilder("gen_ai.client.operation.time_per_output_chunk")
            .setDescription(
                "Time per output chunk, recorded for each chunk received after the first one")
            .setUnit("s");
    try {
      ExtendedDoubleHistogramBuilder extendedBuilder = (ExtendedDoubleHistogramBuilder) builder;
      extendedBuilder.setExplicitBucketBoundariesAdvice(DURATION_BUCKETS);
    } catch (ClassCastException e) {
      logger.log(Level.FINE, "ExtendedDoubleHistogramBuilder not available", e);
    }
    return builder.build();
  }

  public static LongHistogram createTokenHistogram(Meter meter) {
    LongHistogramBuilder builder =
        meter
            .histogramBuilder("gen_ai.client.token.usage")
            .setDescription("Number of input and output tokens used by GenAI clients")
            .setUnit("{token}")
            .ofLongs();
    try {
      ExtendedLongHistogramBuilder extendedBuilder = (ExtendedLongHistogramBuilder) builder;
      extendedBuilder.setExplicitBucketBoundariesAdvice(TOKEN_BUCKETS);
    } catch (ClassCastException e) {
      logger.log(
          Level.FINE,
          "ExtendedLongHistogramBuilder not available, building without advisory buckets",
          e);
    }
    return builder.build();
  }
}
