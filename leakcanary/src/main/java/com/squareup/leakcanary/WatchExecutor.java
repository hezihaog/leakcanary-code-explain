package com.squareup.leakcanary;

/**
 * A {@link WatchExecutor} is in charge of executing a {@link Retryable} in the future, and retry
 * later if needed.
 *
 * 对象监听任务执行器，还支持任务重试
 */
public interface WatchExecutor {
  /**
   * 不执行任务的实现
   */
  WatchExecutor NONE = new WatchExecutor() {
    @Override public void execute(Retryable retryable) {
    }
  };

  /**
   * 执行任务方法
   *
   * @param retryable 可重试任务
   */
  void execute(Retryable retryable);
}
