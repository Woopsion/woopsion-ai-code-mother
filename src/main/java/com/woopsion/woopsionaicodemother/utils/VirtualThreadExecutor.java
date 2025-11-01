package com.woopsion.woopsionaicodemother.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟线程执行器
 * 提供 MDC 传递的虚拟线程池支持
 * 
 * <p>虚拟线程（Virtual Thread）是 Java 21+ 引入的轻量级线程：</p>
 * <ul>
 *   <li>创建成本极低，可以创建数百万个虚拟线程</li>
 *   <li>阻塞操作不会占用平台线程</li>
 *   <li>非常适合 IO 密集型任务</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * // 1. 创建虚拟线程执行器
 * VirtualThreadExecutor executor = VirtualThreadExecutor.create("业务处理");
 * 
 * // 2. 提交任务（自动传递 MDC）
 * executor.submit(() -> {
 *     log.info("这里的日志会包含 traceId、userId 等 MDC 信息");
 *     // 执行业务逻辑
 * });
 * 
 * // 3. 使用 CompletableFuture
 * CompletableFuture<String> future = executor.supplyAsync(() -> {
 *     log.info("异步执行任务");
 *     return "result";
 * });
 * 
 * // 4. 关闭执行器
 * executor.shutdown();
 * }
 * </pre>
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Slf4j
public class VirtualThreadExecutor implements ExecutorService {

    private final ExecutorService delegate;
    private final String name;
    private final AtomicInteger taskCounter;
    private final AtomicInteger activeTaskCount;

    private VirtualThreadExecutor(String name) {
        this.name = name;
        this.taskCounter = new AtomicInteger(0);
        this.activeTaskCount = new AtomicInteger(0);
        
        // 创建虚拟线程执行器
        ThreadFactory factory = Thread.ofVirtual()
                .name(name + "-", 0)
                .factory();
        
        this.delegate = Executors.newThreadPerTaskExecutor(factory);
        
        log.info("虚拟线程执行器创建成功: name={}", name);
    }

    /**
     * 创建虚拟线程执行器
     *
     * @param name 执行器名称（用于线程命名和监控）
     * @return 虚拟线程执行器
     */
    public static VirtualThreadExecutor create(String name) {
        return new VirtualThreadExecutor(name);
    }

    /**
     * 获取执行器统计信息
     *
     * @return 统计信息字符串
     */
    public String getStatistics() {
        return String.format("VirtualThreadExecutor[name=%s, totalTasks=%d, activeTasks=%d]",
                name, taskCounter.get(), activeTaskCount.get());
    }

    /**
     * 获取当前活跃任务数
     *
     * @return 活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    /**
     * 获取总任务数
     *
     * @return 总任务数
     */
    public int getTotalTaskCount() {
        return taskCounter.get();
    }

    /**
     * 包装 Runnable，传递 MDC
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    private Runnable wrapWithMdc(Runnable task) {
        Map<String, String> contextMap = MdcUtils.getCopyOfContextMap();
        int taskId = taskCounter.incrementAndGet();
        
        return () -> {
            activeTaskCount.incrementAndGet();
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                MdcUtils.setContextMap(contextMap);
                log.debug("虚拟线程任务开始: executor={}, taskId={}", name, taskId);
                task.run();
            } catch (Exception e) {
                log.error("虚拟线程任务执行失败: executor={}, taskId={}", name, taskId, e);
                throw e;
            } finally {
                log.debug("虚拟线程任务结束: executor={}, taskId={}", name, taskId);
                MDC.clear();
                MdcUtils.setContextMap(previous);
                activeTaskCount.decrementAndGet();
            }
        };
    }

    /**
     * 包装 Callable，传递 MDC
     *
     * @param task 原始任务
     * @param <T>  返回值类型
     * @return 包装后的任务
     */
    private <T> Callable<T> wrapWithMdc(Callable<T> task) {
        Map<String, String> contextMap = MdcUtils.getCopyOfContextMap();
        int taskId = taskCounter.incrementAndGet();
        
        return () -> {
            activeTaskCount.incrementAndGet();
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                MdcUtils.setContextMap(contextMap);
                log.debug("虚拟线程任务开始: executor={}, taskId={}", name, taskId);
                return task.call();
            } catch (Exception e) {
                log.error("虚拟线程任务执行失败: executor={}, taskId={}", name, taskId, e);
                throw e;
            } finally {
                log.debug("虚拟线程任务结束: executor={}, taskId={}", name, taskId);
                MDC.clear();
                MdcUtils.setContextMap(previous);
                activeTaskCount.decrementAndGet();
            }
        };
    }

    /**
     * 提交任务并返回 CompletableFuture
     *
     * @param task 任务
     * @param <T>  返回值类型
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> supplyAsync(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        submit(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * 提交 Runnable 任务并返回 CompletableFuture
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public CompletableFuture<Void> runAsync(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        submit(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // ==================== ExecutorService 接口实现 ====================

    @Override
    public void shutdown() {
        log.info("关闭虚拟线程执行器: name={}, {}", name, getStatistics());
        delegate.shutdown();
    }

    @Override
    public java.util.List<Runnable> shutdownNow() {
        log.info("立即关闭虚拟线程执行器: name={}, {}", name, getStatistics());
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrapWithMdc(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrapWithMdc(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrapWithMdc(task));
    }

    @Override
    public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(tasks.stream()
                .map(this::wrapWithMdc)
                .toList());
    }

    @Override
    public <T> java.util.List<Future<T>> invokeAll(
            java.util.Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(
                tasks.stream().map(this::wrapWithMdc).toList(),
                timeout,
                unit);
    }

    @Override
    public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks.stream()
                .map(this::wrapWithMdc)
                .toList());
    }

    @Override
    public <T> T invokeAny(
            java.util.Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(
                tasks.stream().map(this::wrapWithMdc).toList(),
                timeout,
                unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrapWithMdc(command));
    }
}

