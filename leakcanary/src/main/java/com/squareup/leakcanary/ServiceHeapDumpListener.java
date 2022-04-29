/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.leakcanary;

import static com.squareup.leakcanary.Preconditions.checkNotNull;

import android.content.Context;

import androidx.annotation.NonNull;

import com.squareup.leakcanary.internal.HeapAnalyzerService;

public final class ServiceHeapDumpListener implements HeapDump.Listener {

  private final Context context;
  private final Class<? extends AbstractAnalysisResultService> listenerServiceClass;

  public ServiceHeapDumpListener(@NonNull final Context context,
      @NonNull final Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
    this.listenerServiceClass = checkNotNull(listenerServiceClass, "listenerServiceClass");
    this.context = checkNotNull(context, "context").getApplicationContext();
  }

  /**
   * 通知Service进行内存泄露分析
   *
   * @param heapDump 堆信息
   */
  @Override public void analyze(@NonNull HeapDump heapDump) {
    //非空判断
    checkNotNull(heapDump, "heapDump");
    //启动Service进行分析
    HeapAnalyzerService.runAnalysis(context, heapDump, listenerServiceClass);
  }
}
