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

import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.EVENT_ONLY;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.NO_CONTENT;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.SPAN_AND_EVENT;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.SPAN_ONLY;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.fromString;
import static com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode.valueOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.loongsuite.otel.util.genai.types.ContentCapturingMode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GenAiConfigUtilTest {

  @AfterEach
  void tearDown() {
    System.clearProperty("otel.instrumentation.genai.multimodal.audio.conversion");
  }

  @Test
  void testContentCapturingModeFromStringNoContent() {
    assertEquals(NO_CONTENT, fromString("no_content"));
  }

  @Test
  void testContentCapturingModeFromStringSpanOnly() {
    assertEquals(SPAN_ONLY, fromString("span_only"));
  }

  @Test
  void testContentCapturingModeFromStringEventOnly() {
    assertEquals(EVENT_ONLY, fromString("event_only"));
  }

  @Test
  void testContentCapturingModeFromStringSpanAndEvent() {
    assertEquals(SPAN_AND_EVENT, fromString("span_and_event"));
  }

  @Test
  void testContentCapturingModeCaseInsensitive() {
    assertEquals(SPAN_ONLY, fromString("SPAN_ONLY"));
    assertEquals(NO_CONTENT, fromString("NO_CONTENT"));
    assertEquals(EVENT_ONLY, fromString("Event_Only"));
    assertEquals(SPAN_AND_EVENT, fromString("Span_And_Event"));
  }

  @Test
  void testContentCapturingModeInvalid() {
    assertThrows(IllegalArgumentException.class, () -> fromString("invalid"));
  }

  @Test
  void testContentCapturingModeInvalidEmpty() {
    assertThrows(IllegalArgumentException.class, () -> fromString(""));
  }

  @Test
  void testContentCapturingModeEnumValues() {
    // Verify all enum constants exist
    ContentCapturingMode[] values = ContentCapturingMode.values();
    assertEquals(4, values.length);
    assertEquals(NO_CONTENT, valueOf("NO_CONTENT"));
    assertEquals(SPAN_ONLY, valueOf("SPAN_ONLY"));
    assertEquals(EVENT_ONLY, valueOf("EVENT_ONLY"));
    assertEquals(SPAN_AND_EVENT, valueOf("SPAN_AND_EVENT"));
  }

  @Test
  void testContentCapturingModeInvalidWithSpaces() {
    assertThrows(IllegalArgumentException.class, () -> fromString(" span_only "));
  }

  @Test
  void multimodalAudioConversionDefaultsToTrue() {
    assertTrue(GenAiConfigUtil.isMultimodalAudioConversionEnabled());
  }

  @Test
  void multimodalAudioConversionCanBeDisabled() {
    System.setProperty("otel.instrumentation.genai.multimodal.audio.conversion", "false");
    assertFalse(GenAiConfigUtil.isMultimodalAudioConversionEnabled());
  }
}
