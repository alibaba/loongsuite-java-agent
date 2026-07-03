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

import org.jspecify.annotations.Nullable;

/**
 * Uploads multimodal payloads to Alibaba Cloud SLS via the Object API ({@code PutObject}).
 *
 * <p>Produces {@code sls://{project}/{logstore}/{objectName}} URIs compatible with ARMS/Robin
 * multimodal trace linking. Use {@link SlsObjectReader#getObject(String)} to read objects back via
 * {@code GetObject}.
 */
public final class SlsUploader implements MultimodalUploader {

  private static final Logger logger = Logger.getLogger(SlsUploader.class.getName());
  private static final int DEFAULT_MAX_QUEUE_SIZE = 1024;

  private final String project;
  private final String logstore;
  private final ExecutorService executor;
  private final Semaphore semaphore;
  private final Map<String, Boolean> uploadedPaths = new LinkedHashMap<>();
  private final SlsMultimodalClient client;

  private SlsUploader(
      String project, String logstore, SlsMultimodalClient client, int maxQueueSize) {
    this.project = project;
    this.logstore = logstore;
    this.client = client;
    this.semaphore = new Semaphore(maxQueueSize);
    this.executor =
        Executors.newFixedThreadPool(
            Math.min(maxQueueSize, 4),
            new ThreadFactory() {
              private int count;

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "genai-multimodal-sls-" + count++);
                t.setDaemon(true);
                return t;
              }
            });
  }

  public static SlsUploader tryCreate(String basePathValue) {
    SlsUriParser.SlsBaseLocation location = SlsUriParser.parseBasePath(basePathValue);
    if (location == null) {
      logger.warning("Invalid SLS base path: " + basePathValue);
      return null;
    }
    SlsMultimodalClient client = SlsMultimodalClient.tryCreate();
    if (client == null) {
      return null;
    }
    return new SlsUploader(location.project, location.logstore, client, parseQueueSize());
  }

  @Override
  public boolean upload(MultimodalUploadItem item) {
    if (uploadedPaths.containsKey(item.targetUrl())) {
      return true;
    }
    if (!semaphore.tryAcquire()) {
      logger.warning("SLS multimodal upload queue full, dropping " + item.targetUrl());
      return false;
    }
    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            try {
              String objectName = SlsUriParser.extractObjectName(item.targetUrl(), project, logstore);
              client.putObject(
                  project, logstore, objectName, item.data(), item.contentType(), item.meta());
              rememberPath(item.targetUrl());
            } catch (Exception e) {
              logger.log(Level.WARNING, "SLS multimodal upload failed for " + item.targetUrl(), e);
            } finally {
              semaphore.release();
            }
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

  private void rememberPath(String path) {
    uploadedPaths.put(path, Boolean.TRUE);
    if (uploadedPaths.size() > 2048) {
      Iterator<String> iterator = uploadedPaths.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
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
