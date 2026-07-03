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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Writes multimodal payloads to the local filesystem. */
public final class LocalFileUploader implements MultimodalUploader {

  private static final Logger logger = Logger.getLogger(LocalFileUploader.class.getName());
  private static final int DEFAULT_MAX_QUEUE_SIZE = 1024;
  private static final int LRU_CACHE_MAX_SIZE = 2048;

  private final Path basePath;
  private final ExecutorService executor;
  private final Semaphore semaphore;
  private final Map<String, Boolean> uploadedPaths = new LinkedHashMap<>();
  private final int lruCacheMaxSize;

  public LocalFileUploader(String basePath, int maxQueueSize) {
    this.basePath = resolveBasePath(basePath);
    this.semaphore = new Semaphore(maxQueueSize);
    this.lruCacheMaxSize = LRU_CACHE_MAX_SIZE;
    this.executor =
        Executors.newFixedThreadPool(
            Math.min(maxQueueSize, 4),
            new ThreadFactory() {
              private int count;

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "genai-multimodal-local-" + count++);
                t.setDaemon(true);
                return t;
              }
            });
    verifyWritable();
  }

  public static LocalFileUploader tryCreate(String basePathValue) {
    try {
      int maxQueueSize = parseQueueSize();
      LocalFileUploader uploader = new LocalFileUploader(basePathValue, maxQueueSize);
      Files.createDirectories(uploader.basePath);
      return uploader;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to initialize LocalFileUploader", e);
      return null;
    }
  }

  @Override
  public boolean upload(MultimodalUploadItem item) {
    Path target = resolveTargetPath(item.targetUrl());
    if (isUploaded(target)) {
      return true;
    }
    if (!semaphore.tryAcquire()) {
      logger.warning("multimodal upload queue full, dropping " + target);
      return false;
    }
    executor.submit(
        () -> {
          try {
            doUpload(target, item);
          } catch (Exception e) {
            logger.log(Level.WARNING, "multimodal upload failed for " + target, e);
          } finally {
            semaphore.release();
          }
        });
    return true;
  }

  @Override
  public void shutdown(long timeoutMs) {
    executor.shutdown();
    try {
      executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void doUpload(Path target, MultimodalUploadItem item) throws Exception {
    if (isUploaded(target)) {
      return;
    }
    Files.createDirectories(target.getParent());
    Files.write(target, item.data());
    writeSidecarMeta(target, item.meta());
    rememberPath(target.toString());
  }

  private void writeSidecarMeta(Path target, Map<String, String> meta) throws Exception {
    if (meta.isEmpty()) {
      return;
    }
    Path metaPath = Paths.get(target.toString() + ".meta");
    Map<String, Object> metaObject = new LinkedHashMap<>(meta);
    Files.write(
        metaPath, GenAiContentSerializer.toJsonString(metaObject).getBytes(StandardCharsets.UTF_8));
  }

  private Path resolveTargetPath(String targetUrl) {
    if (targetUrl.startsWith("file://")) {
      return Paths.get(targetUrl.substring("file://".length()));
    }
    if (targetUrl.startsWith(basePath.toString())) {
      return Paths.get(targetUrl);
    }
    return basePath.resolve(stripBasePrefix(targetUrl));
  }

  private String stripBasePrefix(String targetUrl) {
    String normalizedBase = basePath.toString();
    if (targetUrl.startsWith(normalizedBase + "/")) {
      return targetUrl.substring(normalizedBase.length() + 1);
    }
    int lastSlash = targetUrl.lastIndexOf('/');
    return lastSlash >= 0 ? targetUrl.substring(lastSlash + 1) : targetUrl;
  }

  private boolean isUploaded(Path target) {
    String path = target.toString();
    if (uploadedPaths.containsKey(path)) {
      touchPath(path);
      return true;
    }
    if (Files.exists(target)) {
      rememberPath(path);
      return true;
    }
    return false;
  }

  private void rememberPath(String path) {
    uploadedPaths.put(path, Boolean.TRUE);
    if (uploadedPaths.size() > lruCacheMaxSize) {
      Iterator<String> iterator = uploadedPaths.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
  }

  private void touchPath(String path) {
    uploadedPaths.remove(path);
    uploadedPaths.put(path, Boolean.TRUE);
  }

  private void verifyWritable() {
    try {
      Files.createDirectories(basePath);
      Path testFile = basePath.resolve(".genai_multimodal_write_test");
      Files.write(testFile, new byte[] {0});
      Files.deleteIfExists(testFile);
    } catch (Exception e) {
      throw new IllegalArgumentException("Multimodal base path is not writable: " + basePath, e);
    }
  }

  private static Path resolveBasePath(String value) {
    if (value.startsWith("file://")) {
      return Paths.get(value.substring("file://".length()));
    }
    return Paths.get(value);
  }

  private static int parseQueueSize() {
    String value =
        GenAiConfigUtil.getConfigProperty(
            GenAiEnvironmentVariables.OTEL_INSTRUMENTATION_GENAI_MULTIMODAL_MAX_QUEUE_SIZE);
    if (value == null || value.isEmpty()) {
      return DEFAULT_MAX_QUEUE_SIZE;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed > 0 ? parsed : DEFAULT_MAX_QUEUE_SIZE;
    } catch (NumberFormatException e) {
      return DEFAULT_MAX_QUEUE_SIZE;
    }
  }
}
