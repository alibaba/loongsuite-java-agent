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

import com.alibaba.loongsuite.otel.util.genai.types.BlobPart;
import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.Modality;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.UriPart;

import io.opentelemetry.api.trace.SpanContext;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Preprocesses multimodal {@link BlobPart} values in messages, replacing them with {@link UriPart}
 * references and producing {@link MultimodalUploadItem} tasks for upload.
 */
public final class MultimodalPreUploader {

  private static final int MAX_PARTS = 10;
  private static final int MAX_DATA_SIZE = 30 * 1024 * 1024;
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final String basePath;
  private final boolean processInput;
  private final boolean processOutput;
  private final boolean audioConversionEnabled;

  public MultimodalPreUploader(String basePath) {
    this.basePath = normalizeBasePath(basePath);
    this.processInput = GenAiConfigUtil.shouldProcessMultimodalInput();
    this.processOutput = GenAiConfigUtil.shouldProcessMultimodalOutput();
    this.audioConversionEnabled = GenAiConfigUtil.isMultimodalAudioConversionEnabled();
  }

  /**
   * Processes messages in-place (returns new message lists) and collects upload items.
   *
   * @param spanContext span context for trace/span metadata
   * @param startTimeEpochMillis span start time in milliseconds since epoch
   * @param inputMessages original input messages (may be immutable)
   * @param outputMessages original output messages (may be immutable)
   * @return result containing updated messages and upload tasks
   */
  public ProcessResult process(
      SpanContext spanContext,
      long startTimeEpochMillis,
      List<InputMessage> inputMessages,
      List<OutputMessage> outputMessages) {
    List<MultimodalUploadItem> uploads = new ArrayList<>();
    String traceId = spanContext.isValid() ? spanContext.getTraceId() : null;
    String spanId = spanContext.isValid() ? spanContext.getSpanId() : null;
    long timestampSeconds = startTimeEpochMillis / 1000L;

    List<InputMessage> processedInputs = inputMessages;
    if (processInput && !inputMessages.isEmpty()) {
      processedInputs = processInputMessages(inputMessages, traceId, spanId, timestampSeconds, uploads);
    }

    List<OutputMessage> processedOutputs = outputMessages;
    if (processOutput && !outputMessages.isEmpty()) {
      processedOutputs =
          processOutputMessages(outputMessages, traceId, spanId, timestampSeconds, uploads);
    }

    return new ProcessResult(processedInputs, processedOutputs, uploads);
  }

  /** Extracts multimodal metadata JSON from messages containing {@link UriPart}. */
  @Nullable
  public static String extractMetadataJson(List<? extends MessageHolder> messages) {
    List<Map<String, Object>> metadata = new ArrayList<>();
    for (MessageHolder message : messages) {
      for (MessagePart part : message.parts()) {
        if (part instanceof UriPart) {
          UriPart uriPart = (UriPart) part;
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("type", "uri");
          entry.put("mime_type", uriPart.mimeType());
          entry.put("uri", uriPart.uri());
          entry.put("modality", uriPart.modality());
          metadata.add(entry);
        }
      }
    }
    if (metadata.isEmpty()) {
      return null;
    }
    return GenAiContentSerializer.toJsonString(metadata);
  }

  @Nullable
  public static String extractInputMetadataJson(List<InputMessage> messages) {
    return extractMetadataJson(wrapInputs(messages));
  }

  @Nullable
  public static String extractOutputMetadataJson(List<OutputMessage> messages) {
    return extractMetadataJson(wrapOutputs(messages));
  }

  private List<InputMessage> processInputMessages(
      List<InputMessage> messages,
      @Nullable String traceId,
      @Nullable String spanId,
      long timestampSeconds,
      List<MultimodalUploadItem> uploads) {
    List<InputMessage> result = new ArrayList<>(messages.size());
    for (InputMessage message : messages) {
      List<MessagePart> parts = new ArrayList<>(message.parts());
      processParts(parts, traceId, spanId, timestampSeconds, uploads);
      result.add(new InputMessage(message.role(), parts));
    }
    return result;
  }

  private List<OutputMessage> processOutputMessages(
      List<OutputMessage> messages,
      @Nullable String traceId,
      @Nullable String spanId,
      long timestampSeconds,
      List<MultimodalUploadItem> uploads) {
    List<OutputMessage> result = new ArrayList<>(messages.size());
    for (OutputMessage message : messages) {
      List<MessagePart> parts = new ArrayList<>(message.parts());
      processParts(parts, traceId, spanId, timestampSeconds, uploads);
      result.add(new OutputMessage(message.role(), parts, message.finishReason()));
    }
    return result;
  }

  private void processParts(
      List<MessagePart> parts,
      @Nullable String traceId,
      @Nullable String spanId,
      long timestampSeconds,
      List<MultimodalUploadItem> uploads) {
    int processed = 0;
    for (int idx = 0; idx < parts.size(); idx++) {
      if (processed >= MAX_PARTS) {
        break;
      }
      MessagePart part = parts.get(idx);
      if (!(part instanceof BlobPart)) {
        continue;
      }
      BlobPart blob = (BlobPart) part;
      byte[] data = blob.content();
      if (data.length == 0 || data.length > MAX_DATA_SIZE) {
        continue;
      }
      String mimeType = blob.mimeType() != null ? blob.mimeType() : "application/octet-stream";
      byte[] normalized = data;
      if (audioConversionEnabled && isPcmMime(mimeType)) {
        byte[] wav = pcm16ToWav(data, 16000);
        if (wav != null) {
          normalized = wav;
          mimeType = "audio/wav";
        }
      }
      String modality = Modality.resolve(blob.modality(), mimeType);
      UploadItemAndUri created =
          createUploadItem(normalized, mimeType, modality, traceId, spanId, timestampSeconds);
      uploads.add(created.uploadItem());
      parts.set(idx, created.uriPart());
      processed++;
    }
  }

  private UploadItemAndUri createUploadItem(
      byte[] data,
      String mimeType,
      String modality,
      @Nullable String traceId,
      @Nullable String spanId,
      long timestampSeconds) {
    String ext = extensionFromMimeType(mimeType);
    String md5 = md5Hex(data);
    String date = DATE_FORMAT.format(Instant.ofEpochSecond(timestampSeconds));
    String keyPath = date + "/" + md5 + "." + ext;
    String fullUrl =
        basePath.endsWith("/") ? basePath + keyPath : basePath + "/" + keyPath;

    Map<String, String> meta = new LinkedHashMap<>();
    meta.put("timestamp", String.valueOf(timestampSeconds));
    if (traceId != null) {
      meta.put("traceId", traceId);
    }
    if (spanId != null) {
      meta.put("spanId", spanId);
    }

    String resolvedModality = Modality.resolve(modality, mimeType);

    MultimodalUploadItem uploadItem =
        new MultimodalUploadItem(fullUrl, mimeType, data, meta);
    UriPart uriPart = new UriPart(resolvedModality, mimeType, fullUrl);
    return new UploadItemAndUri(uploadItem, uriPart);
  }

  private static boolean isPcmMime(String mimeType) {
    return "audio/pcm".equals(mimeType)
        || "audio/pcm16".equals(mimeType)
        || "audio/l16".equals(mimeType);
  }

  @Nullable
  private static byte[] pcm16ToWav(byte[] pcmData, int sampleRate) {
    if (pcmData.length == 0) {
      return null;
    }
    int dataSize = pcmData.length;
    int chunkSize = 36 + dataSize;
    byte[] header = new byte[44];
    header[0] = 'R';
    header[1] = 'I';
    header[2] = 'F';
    header[3] = 'F';
    writeIntLe(header, 4, chunkSize);
    header[8] = 'W';
    header[9] = 'A';
    header[10] = 'V';
    header[11] = 'E';
    header[12] = 'f';
    header[13] = 'm';
    header[14] = 't';
    header[15] = ' ';
    writeIntLe(header, 16, 16);
    writeShortLe(header, 20, (short) 1);
    writeShortLe(header, 22, (short) 1);
    writeIntLe(header, 24, sampleRate);
    writeIntLe(header, 28, sampleRate * 2);
    writeShortLe(header, 32, (short) 2);
    writeShortLe(header, 34, (short) 16);
    header[36] = 'd';
    header[37] = 'a';
    header[38] = 't';
    header[39] = 'a';
    writeIntLe(header, 40, dataSize);
    byte[] wav = new byte[header.length + dataSize];
    System.arraycopy(header, 0, wav, 0, header.length);
    System.arraycopy(pcmData, 0, wav, header.length, dataSize);
    return wav;
  }

  private static void writeIntLe(byte[] buffer, int offset, int value) {
    buffer[offset] = (byte) (value & 0xff);
    buffer[offset + 1] = (byte) ((value >> 8) & 0xff);
    buffer[offset + 2] = (byte) ((value >> 16) & 0xff);
    buffer[offset + 3] = (byte) ((value >> 24) & 0xff);
  }

  private static void writeShortLe(byte[] buffer, int offset, short value) {
    buffer[offset] = (byte) (value & 0xff);
    buffer[offset + 1] = (byte) ((value >> 8) & 0xff);
  }

  private static String extensionFromMimeType(String mimeType) {
    switch (mimeType) {
      case "image/jpeg":
        return "jpg";
      case "audio/mpeg":
        return "mp3";
      case "audio/wav":
        return "wav";
      case "audio/pcm":
      case "audio/pcm16":
      case "audio/l16":
        return "pcm";
      default:
        int slash = mimeType.indexOf('/');
        if (slash >= 0 && slash + 1 < mimeType.length()) {
          String ext = mimeType.substring(slash + 1);
          if (!ext.isEmpty() && !"*".equals(ext)) {
            return ext;
          }
        }
        return "bin";
    }
  }

  private static String md5Hex(byte[] data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] hash = digest.digest(data);
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 not available", e);
    }
  }

  private static String normalizeBasePath(String basePath) {
    if (basePath.startsWith("file://")) {
      return basePath;
    }
    return basePath;
  }

  private static List<MessageHolder> wrapInputs(List<InputMessage> messages) {
    List<MessageHolder> holders = new ArrayList<>(messages.size());
    for (InputMessage message : messages) {
      holders.add(message::parts);
    }
    return holders;
  }

  private static List<MessageHolder> wrapOutputs(List<OutputMessage> messages) {
    List<MessageHolder> holders = new ArrayList<>(messages.size());
    for (OutputMessage message : messages) {
      holders.add(message::parts);
    }
    return holders;
  }

  /** Result of multimodal preprocessing. */
  public static final class ProcessResult {
    private final List<InputMessage> inputMessages;
    private final List<OutputMessage> outputMessages;
    private final List<MultimodalUploadItem> uploadItems;

    ProcessResult(
        List<InputMessage> inputMessages,
        List<OutputMessage> outputMessages,
        List<MultimodalUploadItem> uploadItems) {
      this.inputMessages = inputMessages;
      this.outputMessages = outputMessages;
      this.uploadItems = uploadItems;
    }

    public List<InputMessage> inputMessages() {
      return inputMessages;
    }

    public List<OutputMessage> outputMessages() {
      return outputMessages;
    }

    public List<MultimodalUploadItem> uploadItems() {
      return uploadItems;
    }
  }

  private interface MessageHolder {
    List<MessagePart> parts();
  }

  private static final class UploadItemAndUri {
    private final MultimodalUploadItem uploadItem;
    private final UriPart uriPart;

    UploadItemAndUri(MultimodalUploadItem uploadItem, UriPart uriPart) {
      this.uploadItem = uploadItem;
      this.uriPart = uriPart;
    }

    MultimodalUploadItem uploadItem() {
      return uploadItem;
    }

    UriPart uriPart() {
      return uriPart;
    }
  }
}
