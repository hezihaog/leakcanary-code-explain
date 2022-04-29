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

import android.app.Activity;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.squareup.leakcanary.RefWatcher;

/**
 * 监听Support包的Fragment，该类会被反射创建和调用
 */
class SupportFragmentRefWatcher implements FragmentRefWatcher {
  private final RefWatcher refWatcher;

  SupportFragmentRefWatcher(RefWatcher refWatcher) {
    this.refWatcher = refWatcher;
  }

  private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
      new FragmentManager.FragmentLifecycleCallbacks() {

        @Override public void onFragmentViewDestroyed(FragmentManager fm, Fragment fragment) {
          //监听Fragment的View是否内存泄露
          View view = fragment.getView();
          if (view != null) {
            refWatcher.watch(view);
          }
        }

        @Override public void onFragmentDestroyed(FragmentManager fm, Fragment fragment) {
          //监听Fragment对象是否内存泄露
          refWatcher.watch(fragment);
        }
      };

  @Override public void watchFragments(Activity activity) {
    //获取FragmentManager，注册Fragment的生命周期回调
    if (activity instanceof FragmentActivity) {
      FragmentManager supportFragmentManager =
          ((FragmentActivity) activity).getSupportFragmentManager();
      supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
    }
  }
}
