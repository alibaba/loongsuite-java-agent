/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.context.Context;
import java.lang.ref.WeakReference;

// everything is public since called directly from advice code
// (which is inlined into other packages)
public class HttpUrlState {
  public Context context;
  private final WeakReference<Context> contextWeakReference;
  public boolean finished;
  // by default 0 is ignored
  public int statusCode = 0;

  public HttpUrlState(Context context) {
    this.context = context;
    this.contextWeakReference = new WeakReference<>(context);
  }

  public Context getContext() {
    return contextWeakReference.get();
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
    this.context = null;
  }
}
