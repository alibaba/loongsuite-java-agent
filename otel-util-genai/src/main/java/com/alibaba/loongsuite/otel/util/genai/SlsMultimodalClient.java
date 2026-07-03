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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

/**
 * Reflection wrapper for SLS multimodal Object API ({@code PutObject} / {@code GetObject}).
 *
 * <p>Requires optional {@code com.aliyun.openservices:aliyun-log} &gt;= 0.6.155.
 */
final class SlsMultimodalClient {

  private static final Logger logger = Logger.getLogger(SlsMultimodalClient.class.getName());
  static final String MIN_SDK_VERSION_HINT = "0.6.155";
  private static final String META_PREFIX = "x-log-meta-";

  private final Object client;

  private SlsMultimodalClient(Object client) {
    this.client = client;
  }

  @Nullable
  static SlsMultimodalClient tryCreate() {
    try {
      Class<?> clientClass = Class.forName("com.aliyun.openservices.log.Client");
      clientClass.getMethod(
          "putObject", String.class, String.class, String.class, InputStream.class, Map.class);
      clientClass.getMethod("getObject", String.class, String.class, String.class);

      String endpoint = firstEnv("ALIBABA_CLOUD_SLS_ENDPOINT", "SLS_ENDPOINT");
      String accessKeyId =
          firstEnv("ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIYUN_ACCESS_KEY_ID", "SLS_ACCESS_KEY_ID");
      String accessKeySecret =
          firstEnv(
              "ALIBABA_CLOUD_ACCESS_KEY_SECRET",
              "ALIYUN_ACCESS_KEY_SECRET",
              "SLS_ACCESS_KEY_SECRET");
      if (endpoint == null || accessKeyId == null || accessKeySecret == null) {
        logger.warning("SLS credentials not configured; multimodal SLS client disabled");
        return null;
      }
      Object clientInstance =
          clientClass
              .getConstructor(String.class, String.class, String.class)
              .newInstance(endpoint, accessKeyId, accessKeySecret);
      configureAuth(clientInstance, endpoint);
      return new SlsMultimodalClient(clientInstance);
    } catch (ClassNotFoundException e) {
      logger.warning("aliyun-log SDK not on classpath; SLS multimodal client disabled");
      return null;
    } catch (NoSuchMethodException e) {
      logger.warning(
          "aliyun-log SDK does not support PutObject/GetObject; require version "
              + MIN_SDK_VERSION_HINT
              + " or later");
      return null;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to initialize SLS client", e);
      return null;
    }
  }

  private static void configureAuth(Object client, String endpoint) throws Exception {
    Class<?> signVersionClass =
        Class.forName("com.aliyun.openservices.log.http.signer.SignVersion");
    Object v4 = signVersionClass.getField("V4").get(null);
    client.getClass().getMethod("setSignatureVersion", signVersionClass).invoke(client, v4);

    String region = firstEnv("ALIBABA_CLOUD_SLS_REGION", "ALIBABA_CLOUD_REGION", "SLS_REGION");
    if (region == null) {
      region = parseRegionFromEndpoint(endpoint);
    }
    if (region != null && !region.isEmpty()) {
      client.getClass().getMethod("setRegion", String.class).invoke(client, region);
    }
  }

  @Nullable
  private static String parseRegionFromEndpoint(String endpoint) {
    String host = endpoint.trim();
    if (host.startsWith("http://")) {
      host = host.substring("http://".length());
    } else if (host.startsWith("https://")) {
      host = host.substring("https://".length());
    }
    int dot = host.indexOf('.');
    if (dot <= 0) {
      return null;
    }
    return host.substring(0, dot);
  }

  void putObject(
      String project,
      String logstore,
      String objectName,
      byte[] data,
      String contentType,
      Map<String, String> meta)
      throws Exception {
    Map<String, String> headers = buildUploadHeaders(contentType, meta);
    InputStream content = new ByteArrayInputStream(data);
    client
        .getClass()
        .getMethod(
            "putObject",
            String.class,
            String.class,
            String.class,
            InputStream.class,
            Map.class)
        .invoke(client, project, logstore, objectName, content, headers);
  }

  SlsObjectData getObject(String objectUri) throws Exception {
    SlsUriParser.SlsObjectLocation location = SlsUriParser.parseObjectUri(objectUri);
    Object response =
        client
            .getClass()
            .getMethod("getObject", String.class, String.class, String.class)
            .invoke(client, location.project, location.logstore, location.objectName);
    try {
      InputStream inputStream = (InputStream) response.getClass().getMethod("getContent").invoke(response);
      byte[] data = readAllBytes(inputStream);
      Object metadata = response.getClass().getMethod("getObjectMetadata").invoke(response);
      String contentType = (String) metadata.getClass().getMethod("getContentType").invoke(metadata);
      Map<String, String> meta = extractMeta(metadata);
      return new SlsObjectData(objectUri, data, contentType, meta);
    } finally {
      response.getClass().getMethod("close").invoke(response);
    }
  }

  private static Map<String, String> buildUploadHeaders(String contentType, Map<String, String> meta) {
    Map<String, String> headers = new LinkedHashMap<String, String>();
    if (contentType != null && !contentType.isEmpty()) {
      headers.put("Content-Type", contentType);
    }
    // x-log-meta-* custom headers currently break V4 signing in aliyun-log 0.6.155.
    // Metadata is omitted until the SDK supports signed meta headers on PutObject.
    if (meta != null && !meta.isEmpty()) {
      logger.fine("Skipping x-log-meta headers on PutObject due to SDK signing limitation");
    }
    return headers;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> extractMeta(Object metadata) throws Exception {
    Map<String, String> customHeaders =
        (Map<String, String>) metadata.getClass().getMethod("getCustomHeaders").invoke(metadata);
    Map<String, String> meta = new LinkedHashMap<String, String>();
    if (customHeaders == null) {
      return meta;
    }
    for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
      String key = entry.getKey();
      if (key != null && key.startsWith(META_PREFIX)) {
        meta.put(key.substring(META_PREFIX.length()), entry.getValue());
      }
    }
    return meta;
  }

  private static byte[] readAllBytes(InputStream inputStream) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = inputStream.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
    }
    return buffer.toByteArray();
  }

  @Nullable
  private static String firstEnv(String... names) {
    for (String name : names) {
      String value = System.getenv(name);
      if (value != null && !value.isEmpty()) {
        return value.trim();
      }
      value = System.getProperty(name);
      if (value != null && !value.isEmpty()) {
        return value.trim();
      }
    }
    return null;
  }
}
