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

import static com.squareup.leakcanary.HeapDumper.RETRY_LATER;
import static com.squareup.leakcanary.Preconditions.checkNotNull;
import static com.squareup.leakcanary.Retryable.Result.DONE;
import static com.squareup.leakcanary.Retryable.Result.RETRY;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.io.File;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Watches references that should become weakly reachable. When the {@link RefWatcher} detects that
 * a reference might not be weakly reachable when it should, it triggers the {@link HeapDumper}.
 *
 * <p>This class is thread-safe: you can call {@link #watch(Object)} from any thread.
 */
public final class RefWatcher {

  public static final RefWatcher DISABLED = new RefWatcherBuilder<>().build();

  private final WatchExecutor watchExecutor;
  private final DebuggerControl debuggerControl;
  private final GcTrigger gcTrigger;
  private final HeapDumper heapDumper;
  private final HeapDump.Listener heapdumpListener;
  private final HeapDump.Builder heapDumpBuilder;
  private final Set<String> retainedKeys;
  private final ReferenceQueue<Object> queue;

  RefWatcher(WatchExecutor watchExecutor, DebuggerControl debuggerControl, GcTrigger gcTrigger,
      HeapDumper heapDumper, HeapDump.Listener heapdumpListener, HeapDump.Builder heapDumpBuilder) {
    this.watchExecutor = checkNotNull(watchExecutor, "watchExecutor");
    this.debuggerControl = checkNotNull(debuggerControl, "debuggerControl");
    this.gcTrigger = checkNotNull(gcTrigger, "gcTrigger");
    this.heapDumper = checkNotNull(heapDumper, "heapDumper");
    this.heapdumpListener = checkNotNull(heapdumpListener, "heapdumpListener");
    this.heapDumpBuilder = heapDumpBuilder;
    retainedKeys = new CopyOnWriteArraySet<>();
    queue = new ReferenceQueue<>();
  }

  /**
   * Identical to {@link #watch(Object, String)} with an empty string reference name.
   *
   * 和watch(object, string)方法相同，只是第二个参数 referenceName 为空字符串
   *
   * @see #watch(Object, String)
   */
  public void watch(Object watchedReference) {
    watch(watchedReference, "");
  }

  /**
   * Watches the provided references and checks if it can be GCed. This method is non blocking,
   * the check is done on the {@link WatchExecutor} this {@link RefWatcher} has been constructed
   * with.
   *
   * @param referenceName An logical identifier for the watched object.
   */
  public void watch(Object watchedReference, String referenceName) {
    if (this == DISABLED) {
      return;
    }
    //参数判空
    checkNotNull(watchedReference, "watchedReference");
    checkNotNull(referenceName, "referenceName");
    //获取当前时间戳
    final long watchStartNanoTime = System.nanoTime();
    //生成一个唯一Key，用于标识泄露的对象
    String key = UUID.randomUUID().toString();
    //把唯一Key存起来
    retainedKeys.add(key);
    //创建一个弱引用包裹需要监听内存泄漏的对象，并绑定queue弱引用队列，当弱引用被回收时，会把弱引用放进这个队列中
    final KeyedWeakReference reference =
        new KeyedWeakReference(watchedReference, key, referenceName, queue);

    //开始定时监听对象是否被GC回收
    ensureGoneAsync(watchStartNanoTime, reference);
  }

  /**
   * LeakCanary will stop watching any references that were passed to {@link #watch(Object, String)}
   * so far.
   */
  public void clearWatchedReferences() {
    retainedKeys.clear();
  }

  boolean isEmpty() {
    removeWeaklyReachableReferences();
    return retainedKeys.isEmpty();
  }

  HeapDump.Builder getHeapDumpBuilder() {
    return heapDumpBuilder;
  }

  Set<String> getRetainedKeys() {
    return new HashSet<>(retainedKeys);
  }

  /**
   * 开始定时监听对象是否被GC回收
   * @param watchStartNanoTime 对象开始监听的时间
   * @param reference 要监听的对象
   */
  private void ensureGoneAsync(final long watchStartNanoTime, final KeyedWeakReference reference) {
    //通过线程池发出任务
    watchExecutor.execute(new Retryable() {
      @Override public Result run() {
        //执行任务
        return ensureGone(reference, watchStartNanoTime);
      }
    });
  }

  @SuppressWarnings("ReferenceEquality") // Explicitly checking for named null.
  Retryable.Result ensureGone(final KeyedWeakReference reference, final long watchStartNanoTime) {
    //记录GC开始时间
    long gcStartNanoTime = System.nanoTime();
    //计算对象监听时间到任务执行时间的时长
    long watchDurationMs = NANOSECONDS.toMillis(gcStartNanoTime - watchStartNanoTime);

    //移除已经被回收掉的弱引用对象
    removeWeaklyReachableReferences();

    if (debuggerControl.isDebuggerAttached()) {
      // The debugger can create false leaks.
      return RETRY;
    }

    //查询一下，这个弱引用对象是否已被回收
    if (gone(reference)) {
      //已被回收，返回任务结果为结束
      return DONE;
    }

    //通知一次GC
    gcTrigger.runGc();
    //再次移除已经被回收掉的弱引用对象的Key
    removeWeaklyReachableReferences();
    //再查询一下，这个弱引用对象是否已被回收
    if (!gone(reference)) {//还是没有被回收，可能是对象被内存泄露了
      //记录Dump堆的时间
      long startDumpHeap = System.nanoTime();
      //计算出GC花费的时长
      long gcDurationMs = NANOSECONDS.toMillis(startDumpHeap - gcStartNanoTime);

      //执行Dump堆，生成.hprof文件
      File heapDumpFile = heapDumper.dumpHeap();
      //发现Dump失败了，那么再重试
      if (heapDumpFile == RETRY_LATER) {
        // Could not dump the heap.
        return RETRY;
      }
      //Dump成功了，计算花费的时长
      long heapDumpDurationMs = NANOSECONDS.toMillis(System.nanoTime() - startDumpHeap);

      //保存执行过程中的信息
      HeapDump heapDump = heapDumpBuilder.heapDumpFile(heapDumpFile).referenceKey(reference.key)
          .referenceName(reference.name)
          .watchDurationMs(watchDurationMs)
          .gcDurationMs(gcDurationMs)
          .heapDumpDurationMs(heapDumpDurationMs)
          .build();

      //通知监听器，开始分析堆内存信息
      heapdumpListener.analyze(heapDump);
    }
    //对象被回收了，那么返回执行任务为完成
    return DONE;
  }

  /**
   * 检查弱引用的Key是否还在Set中，如果不存在就代表已经被GC回收了
   *
   * @param reference 要被检查的对象
   * @return true代表对象已被回收
   */
  private boolean gone(KeyedWeakReference reference) {
    return !retainedKeys.contains(reference.key);
  }

  /**
   * 移除已经被回收掉的弱引用对象
   */
  private void removeWeaklyReachableReferences() {
    // WeakReferences are enqueued as soon as the object to which they point to becomes weakly
    // reachable. This is before finalization or garbage collection has actually happened.
    KeyedWeakReference ref;
    //通过一个while循环，不断从队列中获取被回收的弱引用对象，如果能获取到，就是有对象被回调，那么把它从Set中移除
    while ((ref = (KeyedWeakReference) queue.poll()) != null) {
      retainedKeys.remove(ref.key);
    }
  }
}
