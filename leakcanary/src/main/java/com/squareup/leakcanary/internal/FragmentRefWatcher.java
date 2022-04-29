/*
 * Copyright (C) 2018 Square, Inc.
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
package com.squareup.leakcanary.internal;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.O;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import com.squareup.leakcanary.RefWatcher;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal class used to watch for fragments leaks.
 */
public interface FragmentRefWatcher {

  /**
   * 监听Activity上Fragment的内存泄露
   */
  void watchFragments(Activity activity);

  final class Helper {

    private static final String SUPPORT_FRAGMENT_REF_WATCHER_CLASS_NAME =
        "com.squareup.leakcanary.internal.SupportFragmentRefWatcher";

    /**
     * 开始监听Fragment内存泄露
     */
    public static void install(Context context, RefWatcher refWatcher) {
      List<FragmentRefWatcher> fragmentRefWatchers = new ArrayList<>();

      //Android 8.0，监听android.app.Fragment包下的Fragment
      if (SDK_INT >= O) {
        fragmentRefWatchers.add(new AndroidOFragmentRefWatcher(refWatcher));
      }

      //监听Support包的Fragment，具体实现在SupportFragmentRefWatcher中
      try {
        Class<?> fragmentRefWatcherClass = Class.forName(SUPPORT_FRAGMENT_REF_WATCHER_CLASS_NAME);
        Constructor<?> constructor =
            fragmentRefWatcherClass.getDeclaredConstructor(RefWatcher.class);
        FragmentRefWatcher supportFragmentRefWatcher =
            (FragmentRefWatcher) constructor.newInstance(refWatcher);
        fragmentRefWatchers.add(supportFragmentRefWatcher);
      } catch (Exception ignored) {
      }

      //android.app.Fragment包、Support包的Fragment，都不能监听，那么return
      if (fragmentRefWatchers.size() == 0) {
        return;
      }

      Helper helper = new Helper(fragmentRefWatchers);
      //注册监听Activity的生命周期
      Application application = (Application) context.getApplicationContext();
      application.registerActivityLifecycleCallbacks(helper.activityLifecycleCallbacks);
    }

    private final Application.ActivityLifecycleCallbacks activityLifecycleCallbacks =
        new ActivityLifecycleCallbacksAdapter() {
          @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            //Activity创建，监听Fragment内存泄露
            for (FragmentRefWatcher watcher : fragmentRefWatchers) {
              watcher.watchFragments(activity);
            }
          }
        };

    private final List<FragmentRefWatcher> fragmentRefWatchers;

    private Helper(List<FragmentRefWatcher> fragmentRefWatchers) {
      this.fragmentRefWatchers = fragmentRefWatchers;
    }
  }
}
