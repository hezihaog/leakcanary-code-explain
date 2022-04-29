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

import static com.squareup.leakcanary.Retryable.Result.RETRY;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.MessageQueue;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * {@link WatchExecutor} suitable for watching Android reference leaks. This executor waits for the
 * main thread to be idle then posts to a serial background thread with the delay specified by
 * {@link AndroidRefWatcherBuilder#watchDelay(long, TimeUnit)}.
 */
public final class AndroidWatchExecutor implements WatchExecutor {

  static final String LEAK_CANARY_THREAD_NAME = "LeakCanary-Heap-Dump";
  private final Handler mainHandler;
  private final Handler backgroundHandler;
  private final long initialDelayMillis;
  private final long maxBackoffFactor;

  public AndroidWatchExecutor(long initialDelayMillis) {
    mainHandler = new Handler(Looper.getMainLooper());
    HandlerThread handlerThread = new HandlerThread(LEAK_CANARY_THREAD_NAME);
    handlerThread.start();
    backgroundHandler = new Handler(handlerThread.getLooper());
    this.initialDelayMillis = initialDelayMillis;
    maxBackoffFactor = Long.MAX_VALUE / initialDelayMillis;
  }

  @Override public void execute(@NonNull Retryable retryable) {
    //如果是主线程，发送闲时消息来处理任务
    if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
      waitForIdle(retryable, 0);
    } else {
      //非主线程，把任务发到主线程，再重新执行 waitForIdle() 方法
      postWaitForIdle(retryable, 0);
    }
  }

  /**
   * 确保在主线程中执行任务
   *
   * @param retryable 任务
   * @param failedAttempts 重试次数
   */
  private void postWaitForIdle(final Retryable retryable, final int failedAttempts) {
    mainHandler.post(new Runnable() {
      @Override public void run() {
        waitForIdle(retryable, failedAttempts);
      }
    });
  }

  /**
   * 发送闲时消息来执行任务
   *
   * @param retryable 任务
   * @param failedAttempts 重试次数
   */
  private void waitForIdle(final Retryable retryable, final int failedAttempts) {
    // This needs to be called from the main thread.
    //这个方法，必须在主线程调用
    Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
      @Override public boolean queueIdle() {
        //闲时消息执行
        postToBackgroundWithDelay(retryable, failedAttempts);
        return false;
      }
    });
  }

  /**
   * 延时指定时间再执行
   *
   * @param retryable 任务
   * @param failedAttempts 重试次数
   */
  private void postToBackgroundWithDelay(final Retryable retryable, final int failedAttempts) {
    long exponentialBackoffFactor = (long) Math.min(Math.pow(2, failedAttempts), maxBackoffFactor);
    //计算延时时间
    long delayMillis = initialDelayMillis * exponentialBackoffFactor;
    backgroundHandler.postDelayed(new Runnable() {
      @Override public void run() {
        //执行任务
        Retryable.Result result = retryable.run();
        //如果任务结果是重试，那么再发一次闲时消息，再执行一次
        if (result == RETRY) {
          postWaitForIdle(retryable, failedAttempts + 1);
        }
      }
    }, delayMillis);
  }
}
