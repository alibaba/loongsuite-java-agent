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

import org.jspecify.annotations.Nullable;

/** Parses {@code sls://} URIs for multimodal object storage. */
final class SlsUriParser {

  private SlsUriParser() {}

  /** Parses {@code sls://{project}/{logstore}} base path. */
  @Nullable
  static SlsBaseLocation parseBasePath(String basePath) {
    if (basePath == null || !basePath.startsWith("sls://")) {
      return null;
    }
    String remainder = basePath.substring("sls://".length());
    int slash = remainder.indexOf('/');
    if (slash <= 0 || slash >= remainder.length() - 1) {
      return null;
    }
    return new SlsBaseLocation(remainder.substring(0, slash), remainder.substring(slash + 1));
  }

  /** Parses full object URI {@code sls://{project}/{logstore}/{objectName}}. */
  static SlsObjectLocation parseObjectUri(String objectUri) {
    if (objectUri == null || !objectUri.startsWith("sls://")) {
      throw new IllegalArgumentException("Invalid SLS object URI: " + objectUri);
    }
    String remainder = objectUri.substring("sls://".length());
    int firstSlash = remainder.indexOf('/');
    if (firstSlash <= 0) {
      throw new IllegalArgumentException("Invalid SLS object URI (missing logstore): " + objectUri);
    }
    int secondSlash = remainder.indexOf('/', firstSlash + 1);
    if (secondSlash <= firstSlash + 1 || secondSlash >= remainder.length() - 1) {
      throw new IllegalArgumentException("Invalid SLS object URI (missing object name): " + objectUri);
    }
    String project = remainder.substring(0, firstSlash);
    String logstore = remainder.substring(firstSlash + 1, secondSlash);
    String objectName = remainder.substring(secondSlash + 1);
    return new SlsObjectLocation(project, logstore, objectName);
  }

  /**
   * Extracts object name when URI shares the configured base path.
   *
   * <p>Example: {@code sls://my-project/my-logstore/20260702/abc.pcm} → {@code 20260702/abc.pcm}
   */
  static String extractObjectName(String targetUrl, String project, String logstore) {
    String prefix = "sls://" + project + "/" + logstore + "/";
    if (targetUrl.startsWith(prefix)) {
      return targetUrl.substring(prefix.length());
    }
    throw new IllegalArgumentException(
        "SLS target URL does not match configured base path: " + targetUrl);
  }

  static final class SlsBaseLocation {
    final String project;
    final String logstore;

    SlsBaseLocation(String project, String logstore) {
      this.project = project;
      this.logstore = logstore;
    }
  }

  static final class SlsObjectLocation {
    final String project;
    final String logstore;
    final String objectName;

    SlsObjectLocation(String project, String logstore, String objectName) {
      this.project = project;
      this.logstore = logstore;
      this.objectName = objectName;
    }
  }
}
