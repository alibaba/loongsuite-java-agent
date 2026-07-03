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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SlsUriParserTest {

  @Test
  void parseObjectUri() {
    SlsUriParser.SlsObjectLocation location =
        SlsUriParser.parseObjectUri(
            "sls://my-project/my-logstore/20260702/abc123.pcm");
    assertEquals("my-project", location.project);
    assertEquals("my-logstore", location.logstore);
    assertEquals("20260702/abc123.pcm", location.objectName);
  }

  @Test
  void extractObjectNameFromFullUri() {
    String objectName =
        SlsUriParser.extractObjectName(
            "sls://my-project/my-logstore/20260702/abc123.pcm", "my-project", "my-logstore");
    assertEquals("20260702/abc123.pcm", objectName);
  }

  @Test
  void extractObjectNameRejectsMismatchedBasePath() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SlsUriParser.extractObjectName(
                "sls://other-project/my-logstore/20260702/abc123.pcm",
                "my-project",
                "my-logstore"));
  }
}
