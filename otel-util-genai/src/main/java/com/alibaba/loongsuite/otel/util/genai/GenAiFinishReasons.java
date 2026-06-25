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

import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/** Resolves {@code gen_ai.response.finish_reasons} from explicit values or output messages. */
final class GenAiFinishReasons {

  private GenAiFinishReasons() {}

  @Nullable
  static List<String> resolve(
      @Nullable List<String> explicitFinishReasons, List<OutputMessage> outputMessages) {
    if (explicitFinishReasons != null) {
      return explicitFinishReasons.isEmpty() ? null : explicitFinishReasons;
    }
    if (outputMessages.isEmpty()) {
      return null;
    }
    List<String> reasons = new ArrayList<>();
    for (OutputMessage message : outputMessages) {
      if (message.finishReason() != null && !message.finishReason().isEmpty()) {
        reasons.add(message.finishReason());
      }
    }
    return reasons.isEmpty() ? null : reasons;
  }
}
