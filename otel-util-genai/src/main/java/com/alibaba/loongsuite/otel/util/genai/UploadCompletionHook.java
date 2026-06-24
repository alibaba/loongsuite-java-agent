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

import com.alibaba.loongsuite.otel.util.genai.types.InputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.MessagePart;
import com.alibaba.loongsuite.otel.util.genai.types.OutputMessage;
import com.alibaba.loongsuite.otel.util.genai.types.TextPart;
import com.alibaba.loongsuite.otel.util.genai.types.ToolDefinition;
import io.opentelemetry.api.trace.Span;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Completion hook that uploads GenAI content to external storage and stamps {@code *_ref}
 * attributes on spans and events.
 *
 * <p>Activated by setting {@link GenAiEnvironmentVariables#OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK}
 * to {@code upload} and configuring {@link
 * GenAiEnvironmentVariables#OTEL_INSTRUMENTATION_GENAI_UPLOAD_BASE_PATH}.
 */
public final class UploadCompletionHook implements CompletionHook {

  private static final Logger logger = Logger.getLogger(UploadCompletionHook.class.getName());
  private static final int DEFAULT_MAX_QUEUE_SIZE = 20;
  private static final String DEFAULT_FORMAT = "json";

  private final Path basePath;
  private final String format;
  private final ExecutorService executor;
  private final Semaphore semaphore;
  private final Map<String, Boolean> pathCache = new LinkedHashMap<>();
  private final int pathCacheMaxSize;

  private UploadCompletionHook(
      Path basePath, String format, int maxQueueSize, int pathCacheMaxSize) {
    this.basePath = basePath;
    this.format = format;
    this.semaphore = new Semaphore(maxQueueSize);
    this.pathCacheMaxSize = pathCacheMaxSize;
    this.executor =
        Executors.newFixedThreadPool(
            Math.min(maxQueueSize, 64),
            new ThreadFactory() {
              private int count;

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "genai-upload-" + count++);
                t.setDaemon(true);
                return t;
              }
            });
    verifyWritable();
  }

  /**
   * Creates an upload hook from environment configuration, or a no-op if misconfigured.
   *
   * <p>Set {@code OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK=upload} and configure the upload base
   * path.
   */
  public static CompletionHook tryCreate() {
    String basePathValue =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_UPLOAD_BASE_PATH);
    if (basePathValue == null || basePathValue.isEmpty()) {
      logger.warning(
          "OTEL_INSTRUMENTATION_GENAI_COMPLETION_HOOK=upload requires "
              + GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_UPLOAD_BASE_PATH);
      return NoOpCompletionHook.INSTANCE;
    }

    String format =
        normalizeFormat(
            GenAiConfigUtil.getConfigProperty(
                GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_UPLOAD_FORMAT));
    int maxQueueSize =
        parseQueueSize(
            GenAiConfigUtil.getConfigProperty(
                GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_UPLOAD_MAX_QUEUE_SIZE));

    try {
      Path basePath = resolveBasePath(basePathValue);
      Files.createDirectories(basePath);
      return new UploadCompletionHook(basePath, format, maxQueueSize, 1024);
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to initialize UploadCompletionHook", e);
      return NoOpCompletionHook.INSTANCE;
    }
  }

  @Override
  public void onCompletion(CompletionHookContext context) {
    List<InputMessage> inputs = context.getInputs();
    List<OutputMessage> outputs = context.getOutputs();
    List<MessagePart> systemInstruction = context.getSystemInstruction();
    List<ToolDefinition> toolDefinitions = context.getToolDefinitions();

    if (inputs.isEmpty()
        && outputs.isEmpty()
        && systemInstruction.isEmpty()
        && (toolDefinitions == null || toolDefinitions.isEmpty())) {
      return;
    }

    String uuid = UUID.randomUUID().toString();
    String systemHash = hashTextOnlySystemInstruction(systemInstruction);
    String toolHash = hashToolDefinitions(toolDefinitions);

    Map<String, String> refs = new LinkedHashMap<>();
    if (!inputs.isEmpty()) {
      String path = basePath.resolve(uuid + "_inputs." + format).toString();
      refs.put(GenAiAttributes.GEN_AI_INPUT_MESSAGES_REF.getKey(), path);
      submitUpload(path, false, GenAiContentSerializer.toMapList(inputs));
    }
    if (!outputs.isEmpty()) {
      String path = basePath.resolve(uuid + "_outputs." + format).toString();
      refs.put(GenAiAttributes.GEN_AI_OUTPUT_MESSAGES_REF.getKey(), path);
      submitUpload(path, false, GenAiContentSerializer.toMapList(outputs));
    }
    if (!systemInstruction.isEmpty()) {
      String fileName = (systemHash != null ? systemHash : uuid) + "_system_instruction." + format;
      String path = basePath.resolve(fileName).toString();
      refs.put(GenAiAttributes.GEN_AI_SYSTEM_INSTRUCTIONS_REF.getKey(), path);
      submitUpload(path, systemHash != null, GenAiContentSerializer.toMapList(systemInstruction));
    }
    if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
      String fileName = (toolHash != null ? toolHash : uuid) + "_tool.definitions." + format;
      String path = basePath.resolve(fileName).toString();
      refs.put(GenAiAttributes.GEN_AI_TOOL_DEFINITIONS_REF.getKey(), path);
      submitUpload(path, toolHash != null, GenAiContentSerializer.toMapList(toolDefinitions));
    }

    stampRefs(context.getSpan(), context.getLogRecord(), refs);
  }

  /** Flushes pending uploads. */
  public void shutdown() {
    executor.shutdown();
  }

  private void stampRefs(Span span, @Nullable EventLogRecord logRecord, Map<String, String> refs) {
    for (Map.Entry<String, String> entry : refs.entrySet()) {
      span.setAttribute(entry.getKey(), entry.getValue());
      if (logRecord != null) {
        logRecord.setAttribute(entry.getKey(), entry.getValue());
      }
    }
  }

  private void submitUpload(
      String path, boolean contentHashedToFilename, List<Map<String, Object>> payload) {
    if (contentHashedToFilename && pathExists(path)) {
      return;
    }
    if (!semaphore.tryAcquire()) {
      logger.warning(() -> "upload queue is full, dropping upload " + path);
      return;
    }
    executor.submit(
        () -> {
          try {
            doUpload(path, contentHashedToFilename, payload);
          } catch (Exception e) {
            logger.log(Level.WARNING, "upload failed for " + path, e);
          } finally {
            semaphore.release();
          }
        });
  }

  private void doUpload(
      String path, boolean contentHashedToFilename, List<Map<String, Object>> payload)
      throws IOException {
    if (contentHashedToFilename && pathExists(path)) {
      return;
    }
    Path filePath = Paths.get(path);
    Files.createDirectories(filePath.getParent());

    if ("jsonl".equals(format)) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < payload.size(); i++) {
        Map<String, Object> line = new LinkedHashMap<>(payload.get(i));
        line.put("index", i);
        sb.append(GenAiContentSerializer.toJsonString(line));
        sb.append('\n');
      }
      Files.write(filePath, sb.toString().getBytes(StandardCharsets.UTF_8));
    } else {
      Files.write(
          filePath, GenAiContentSerializer.toJsonString(payload).getBytes(StandardCharsets.UTF_8));
    }

    if (contentHashedToFilename) {
      rememberPath(path);
    }
  }

  private boolean pathExists(String path) {
    if (pathCache.containsKey(path)) {
      touchPath(path);
      return true;
    }
    if (Files.exists(Paths.get(path))) {
      rememberPath(path);
      return true;
    }
    return false;
  }

  private void rememberPath(String path) {
    pathCache.put(path, Boolean.TRUE);
    if (pathCache.size() > pathCacheMaxSize) {
      Iterator<String> iterator = pathCache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
  }

  private void touchPath(String path) {
    pathCache.remove(path);
    pathCache.put(path, Boolean.TRUE);
  }

  private void verifyWritable() {
    Path testFile = basePath.resolve(".one_off_test_to_see_if_upload_works." + format);
    try {
      Files.createDirectories(basePath);
      if ("jsonl".equals(format)) {
        Files.write(testFile, "\n".getBytes(StandardCharsets.UTF_8));
      } else {
        Files.write(testFile, "[]".getBytes(StandardCharsets.UTF_8));
      }
      Files.deleteIfExists(testFile);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Failed to write test file to upload path: " + testFile + ". Error: " + e.getMessage(),
          e);
    }
  }

  private static Path resolveBasePath(String value) {
    if (value.startsWith("file://")) {
      return Paths.get(value.substring("file://".length()));
    }
    return Paths.get(value);
  }

  private static String normalizeFormat(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return DEFAULT_FORMAT;
    }
    String normalized = value.trim().toLowerCase();
    if (!"json".equals(normalized) && !"jsonl".equals(normalized)) {
      logger.warning(() -> "Invalid upload format \"" + value + "\", defaulting to json");
      return DEFAULT_FORMAT;
    }
    return normalized;
  }

  private static int parseQueueSize(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return DEFAULT_MAX_QUEUE_SIZE;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : DEFAULT_MAX_QUEUE_SIZE;
    } catch (NumberFormatException e) {
      logger.warning(() -> "Invalid upload queue size \"" + value + "\", defaulting to 20");
      return DEFAULT_MAX_QUEUE_SIZE;
    }
  }

  @Nullable
  private static String hashTextOnlySystemInstruction(List<MessagePart> parts) {
    if (parts.isEmpty()) {
      return null;
    }
    for (MessagePart part : parts) {
      if (!(part instanceof TextPart)) {
        return null;
      }
    }
    StringBuilder text = new StringBuilder();
    for (MessagePart part : parts) {
      if (text.length() > 0) {
        text.append('\n');
      }
      text.append(((TextPart) part).content());
    }
    return sha256Hex(text.toString());
  }

  @Nullable
  private static String hashToolDefinitions(@Nullable List<ToolDefinition> toolDefinitions) {
    if (toolDefinitions == null || toolDefinitions.isEmpty()) {
      return null;
    }
    try {
      return sha256Hex(GenAiContentSerializer.toJsonString(toolDefinitions));
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
