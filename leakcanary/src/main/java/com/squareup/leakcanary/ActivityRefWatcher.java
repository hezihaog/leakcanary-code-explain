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

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import com.squareup.leakcanary.internal.ActivityLifecycleCallbacksAdapter;

/**
 * @deprecated This was initially part of the LeakCanary API, but should not be any more.
 * {@link AndroidRefWatcherBuilder#watchActivities} should be used instead.
 * We will make this class internal in the next major version.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public final class ActivityRefWatcher {

  public static void installOnIcsPlus(@NonNull Application application,
      @NonNull RefWatcher refWatcher) {
    install(application, refWatcher);
  }

  /**
   * 开始监听Activity内存泄露
   */
  public static void install(@NonNull Context context, @NonNull RefWatcher refWatcher) {
    Application application = (Application) context.getApplicationContext();
    //创建监听对象
    ActivityRefWatcher activityRefWatcher = new ActivityRefWatcher(application, refWatcher);
    //开始监听Activity生命周期
    application.registerActivityLifecycleCallbacks(activityRefWatcher.lifecycleCallbacks);
  }

  /**
   * Activity生命周期回调对象
   */
  private final Application.ActivityLifecycleCallbacks lifecycleCallbacks =
      new ActivityLifecycleCallbacksAdapter() {
        @Override public void onActivityDestroyed(Activity activity) {
          //Activity的onDestroy()被调用，准备销毁时，开始监听对象是否内存泄露
          refWatcher.watch(activity);
        }
      };

  private final Application application;
  private final RefWatcher refWatcher;

  /**
   * Activity内存泄露监听器
   */
  private ActivityRefWatcher(Application application, RefWatcher refWatcher) {
    this.application = application;
    this.refWatcher = refWatcher;
  }

  /**
   * 开始监听
   */
  public void watchActivities() {
    // Make sure you don't get installed twice.
    //确保只会有一次监听，避免重复注册
    stopWatchingActivities();
    application.registerActivityLifecycleCallbacks(lifecycleCallbacks);
  }

  /**
   * 停止监听
   */
  public void stopWatchingActivities() {
    application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
  }
}
