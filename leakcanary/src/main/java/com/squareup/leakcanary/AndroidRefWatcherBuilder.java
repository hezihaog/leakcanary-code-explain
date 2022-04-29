package com.squareup.leakcanary;

import static com.squareup.leakcanary.RefWatcher.DISABLED;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;

import androidx.annotation.NonNull;

import com.squareup.leakcanary.internal.DisplayLeakActivity;
import com.squareup.leakcanary.internal.FragmentRefWatcher;
import com.squareup.leakcanary.internal.LeakCanaryInternals;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** A {@link RefWatcherBuilder} with appropriate Android defaults. */
//AndroidRefWatcherBuilder继承于RefWatcherBuilder，是针对安卓平台使用的
public final class AndroidRefWatcherBuilder extends RefWatcherBuilder<AndroidRefWatcherBuilder> {

  private static final long DEFAULT_WATCH_DELAY_MILLIS = SECONDS.toMillis(5);

  private final Context context;
  private boolean watchActivities = true;
  private boolean watchFragments = true;
  private boolean enableDisplayLeakActivity = false;

  AndroidRefWatcherBuilder(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  /**
   * Sets a custom {@link AbstractAnalysisResultService} to listen to analysis results. This
   * overrides any call to {@link #heapDumpListener(HeapDump.Listener)}.
   *
   * 设置一个自定义的AbstractAnalysisResultService子类，用于分析内存泄露，并把结果发送到通知中
   */
  public @NonNull AndroidRefWatcherBuilder listenerServiceClass(
      @NonNull Class<? extends AbstractAnalysisResultService> listenerServiceClass) {
    enableDisplayLeakActivity = DisplayLeakService.class.isAssignableFrom(listenerServiceClass);
    return heapDumpListener(new ServiceHeapDumpListener(context, listenerServiceClass));
  }

  /**
   * Sets a custom delay for how long the {@link RefWatcher} should wait until it checks if a
   * tracked object has been garbage collected. This overrides any call to {@link
   * #watchExecutor(WatchExecutor)}.
   */
  public @NonNull AndroidRefWatcherBuilder watchDelay(long delay, @NonNull TimeUnit unit) {
    return watchExecutor(new AndroidWatchExecutor(unit.toMillis(delay)));
  }

  /**
   * Whether we should automatically watch activities when calling {@link #buildAndInstall()}.
   * Default is true.
   */
  public @NonNull AndroidRefWatcherBuilder watchActivities(boolean watchActivities) {
    this.watchActivities = watchActivities;
    return this;
  }

  /**
   * Whether we should automatically watch fragments when calling {@link #buildAndInstall()}.
   * Default is true. When true, LeakCanary watches native fragments on Android O+ and support
   * fragments if the leakcanary-support-fragment dependency is in the classpath.
   */
  public @NonNull AndroidRefWatcherBuilder watchFragments(boolean watchFragments) {
    this.watchFragments = watchFragments;
    return this;
  }

  /**
   * Sets the maximum number of heap dumps stored. This overrides any call to
   * {@link LeakCanary#setLeakDirectoryProvider(LeakDirectoryProvider)}
   *
   * @throws IllegalArgumentException if maxStoredHeapDumps < 1.
   */
  public @NonNull AndroidRefWatcherBuilder maxStoredHeapDumps(int maxStoredHeapDumps) {
    LeakDirectoryProvider leakDirectoryProvider =
        new DefaultLeakDirectoryProvider(context, maxStoredHeapDumps);
    LeakCanary.setLeakDirectoryProvider(leakDirectoryProvider);
    return self();
  }

  /**
   * Creates a {@link RefWatcher} instance and makes it available through {@link
   * LeakCanary#installedRefWatcher()}.
   *
   * Also starts watching activity references if {@link #watchActivities(boolean)} was set to true.
   *
   * @throws UnsupportedOperationException if called more than once per Android process.
   */
  public @NonNull RefWatcher buildAndInstall() {
    //不允许重复调用安装方法
    if (LeakCanaryInternals.installedRefWatcher != null) {
      throw new UnsupportedOperationException("buildAndInstall() should only be called once.");
    }
    //创建RefWatcher对象
    RefWatcher refWatcher = build();
    //非禁用状态，进入if
    if (refWatcher != DISABLED) {
      if (enableDisplayLeakActivity) {
        LeakCanaryInternals.setEnabledAsync(context, DisplayLeakActivity.class, true);
      }
      //watchActivities 默认为true，默认允许监听Activity内存泄露
      if (watchActivities) {
        ActivityRefWatcher.install(context, refWatcher);
      }
      //watchFragments 默认为true，默认允许监听Fragment内存泄露
      if (watchFragments) {
        FragmentRefWatcher.Helper.install(context, refWatcher);
      }
    }
    //更新安装完成的标志位
    LeakCanaryInternals.installedRefWatcher = refWatcher;
    return refWatcher;
  }

  @Override protected boolean isDisabled() {
    return LeakCanary.isInAnalyzerProcess(context);
  }

  @Override protected @NonNull HeapDumper defaultHeapDumper() {
    LeakDirectoryProvider leakDirectoryProvider =
        LeakCanaryInternals.getLeakDirectoryProvider(context);
    return new AndroidHeapDumper(context, leakDirectoryProvider);
  }

  @Override protected @NonNull DebuggerControl defaultDebuggerControl() {
    return new AndroidDebuggerControl();
  }

  @Override protected @NonNull HeapDump.Listener defaultHeapDumpListener() {
    return new ServiceHeapDumpListener(context, DisplayLeakService.class);
  }

  @Override protected @NonNull ExcludedRefs defaultExcludedRefs() {
    return AndroidExcludedRefs.createAppDefaults().build();
  }

  @Override protected @NonNull WatchExecutor defaultWatchExecutor() {
    return new AndroidWatchExecutor(DEFAULT_WATCH_DELAY_MILLIS);
  }

  @Override protected @NonNull
  List<Class<? extends Reachability.Inspector>> defaultReachabilityInspectorClasses() {
    return AndroidReachabilityInspectors.defaultAndroidInspectors();
  }
}
