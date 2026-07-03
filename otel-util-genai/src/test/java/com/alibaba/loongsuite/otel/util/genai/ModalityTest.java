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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alibaba.loongsuite.otel.util.genai.types.BlobPart;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.Modality;
import com.alibaba.loongsuite.otel.util.genai.types.UriPart;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ModalityTest {

  @Test
  void fromMimeTypeMapsStandardPrefixes() {
    assertEquals(Modality.IMAGE, Modality.fromMimeType("image/png"));
    assertEquals(Modality.AUDIO, Modality.fromMimeType("audio/wav"));
    assertEquals(Modality.AUDIO, Modality.fromMimeType("audio/mpeg"));
    assertEquals(Modality.VIDEO, Modality.fromMimeType("video/mp4"));
    assertNull(Modality.fromMimeType("application/octet-stream"));
    assertNull(Modality.fromMimeType(null));
  }

  @Test
  void resolvePrefersExplicitModality() {
    assertEquals("custom", Modality.resolve("custom", "audio/wav"));
  }

  @Test
  void resolveFallsBackToMimeType() {
    assertEquals(Modality.AUDIO, Modality.resolve(null, "audio/wav"));
    assertEquals(Modality.AUDIO, Modality.resolve("", "audio/pcm"));
  }

  @Test
  void blobPartInfersModalityFromMimeType() {
    BlobPart part = new BlobPart("audio/wav", new byte[] {1, 2, 3});
    assertEquals(Modality.AUDIO, part.modality());
    assertEquals("audio/wav", part.mimeType());
  }

  @Test
  void uriPartInfersModalityFromMimeType() {
    UriPart part = new UriPart("image/jpeg", "sls://bucket/obj.jpg");
    assertEquals(Modality.IMAGE, part.modality());
  }

  @Test
  void preUploaderInfersModalityWhenOmitted() {
    System.setProperty("otel.instrumentation.genai.multimodal.upload.mode", "input");
    try {
      byte[] wav = new byte[] {0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4};
      List<InputMessage> inputs =
          Collections.singletonList(
              new InputMessage("user", Collections.singletonList(new BlobPart("audio/wav", wav))));

      MultimodalPreUploader preUploader = new MultimodalPreUploader("file:///tmp/genai-test");
      SpanContext spanContext =
          SpanContext.create(
              "00000000000000000000000000000002",
              "0000000000000002",
              TraceFlags.getSampled(),
              TraceState.getDefault());

      MultimodalPreUploader.ProcessResult result =
          preUploader.process(spanContext, System.currentTimeMillis(), inputs, Collections.emptyList());

      MessagePart part = result.inputMessages().get(0).parts().get(0);
      assertInstanceOf(UriPart.class, part);
      assertEquals(Modality.AUDIO, ((UriPart) part).modality());
    } finally {
      System.clearProperty("otel.instrumentation.genai.multimodal.upload.mode");
    }
  }
}
