package com.woopsion.woopsionaicodemother.example;

import com.woopsion.woopsionaicodemother.utils.ReactorMdcUtils;
import com.woopsion.woopsionaicodemother.utils.VirtualThreadExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * MDC 使用示例
 * 演示在各种场景下如何正确使用 MDC
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Slf4j
@Service
public class MdcUsageExamples {

    @Autowired
    @Qualifier("businessThreadPool")
    private ThreadPoolTaskExecutor businessThreadPool;

    @Autowired
    @Qualifier("ioThreadPool")
    private ThreadPoolTaskExecutor ioThreadPool;

    @Autowired
    @Qualifier("businessVirtualThreadPool")
    private VirtualThreadExecutor businessVirtualThreadPool;

    // ==================== 示例 1：同步代码 ====================

    /**
     * 示例 1：普通同步代码
     * MDC 自动在请求入口装载，无需任何额外操作
     */
    public void example1_SyncCode() {
        log.info("开始处理同步任务");
        
        // 执行业务逻辑
        processBusinessLogic();
        
        log.info("同步任务处理完成");
    }

    // ==================== 示例 2：异步线程池 ====================

    /**
     * 示例 2.1：使用业务线程池
     * MDC 会自动传递到子线程
     */
    public void example2_1_AsyncWithBusinessPool() {
        log.info("提交任务到业务线程池");
        
        businessThreadPool.submit(() -> {
            log.info("在业务线程中执行任务");
            // 这里的日志会包含完整的 MDC 信息（traceId、userId 等）
            processBusinessLogic();
        });
    }

    /**
     * 示例 2.2：使用 IO 线程池处理文件
     * 适合 IO 密集型任务
     */
    public void example2_2_AsyncWithIoPool(List<String> files) {
        log.info("开始批量处理文件，数量: {}", files.size());
        
        files.forEach(file -> {
            ioThreadPool.submit(() -> {
                log.info("处理文件: {}", file);
                // IO 操作：读取、处理文件
                processFile(file);
                log.info("文件处理完成: {}", file);
            });
        });
    }

    /**
     * 示例 2.3：使用 CompletableFuture
     * 结合线程池和异步编程
     */
    public CompletableFuture<String> example2_3_CompletableFuture(Long orderId) {
        log.info("异步处理订单: orderId={}", orderId);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        
        businessThreadPool.submit(() -> {
            try {
                log.info("开始处理订单");
                String result = processOrder(orderId);
                future.complete(result);
                log.info("订单处理成功");
            } catch (Exception e) {
                log.error("订单处理失败", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }

    // ==================== 示例 3：响应式编程 ====================

    /**
     * 示例 3.1：基础响应式编程
     * 在需要日志的地方传递 MDC
     */
    public Flux<String> example3_1_BasicReactive() {
        log.info("创建响应式流");
        
        return Flux.just("A", "B", "C", "D")
                // 高频 map 操作：不传递 MDC（性能优化）
                .map(String::toLowerCase)
                // 需要日志的地方：传递 MDC
                .doOnNext(ReactorMdcUtils.withMdc(value -> {
                    log.info("处理数据: {}", value);
                }))
                .doOnComplete(ReactorMdcUtils.withMdc(() -> {
                    log.info("数据处理完成");
                }))
                .doOnError(ReactorMdcUtils.withMdc(error -> {
                    log.error("数据处理失败", error);
                }))
                // 捕获 MDC 上下文
                .contextWrite(ReactorMdcUtils.captureMdc());
    }

    /**
     * 示例 3.2：复杂响应式流
     * 包含 flatMap、filter 等操作符
     */
    public Flux<String> example3_2_ComplexReactive(List<Long> ids) {
        log.info("处理复杂响应式流，数量: {}", ids.size());
        
        return Flux.fromIterable(ids)
                // 高频操作：不传递 MDC
                .filter(id -> id > 0)
                .map(id -> "ID-" + id)
                // flatMap 可能失败，内部处理日志
                .flatMap(idStr -> processAsync(idStr)
                        .doOnError(ReactorMdcUtils.withMdc(error -> {
                            log.error("处理失败: {}", idStr, error);
                        }))
                        .onErrorResume(e -> Mono.empty())
                )
                // 在关键节点传递 MDC
                .doOnNext(ReactorMdcUtils.withMdc(result -> {
                    log.info("处理结果: {}", result);
                }))
                .contextWrite(ReactorMdcUtils.captureMdc());
    }

    /**
     * 示例 3.3：SSE 流式输出（性能敏感）
     * 仅在必要时传递 MDC
     */
    public Flux<String> example3_3_SseStream(String prompt) {
        log.info("开始流式输出: prompt={}", prompt);
        
        return generateStreamData(prompt)
                // ❌ 不在高频 map 中传递 MDC（可能每秒数百次）
                .map(chunk -> wrapToJson(chunk))
                // ✅ 只在错误和完成时传递 MDC
                .doOnError(ReactorMdcUtils.withMdc(error -> {
                    log.error("流式输出错误", error);
                }))
                .doOnComplete(ReactorMdcUtils.withMdc(() -> {
                    log.info("流式输出完成");
                }))
                .contextWrite(ReactorMdcUtils.captureMdc());
    }

    /**
     * 示例 3.4：使用 deferMonoWithMdc
     * 延迟执行时传递 MDC
     */
    public Mono<String> example3_4_DeferWithMdc(Long userId) {
        return ReactorMdcUtils.deferMonoWithMdc(() -> {
            log.info("延迟执行任务: userId={}", userId);
            return Mono.just("user-" + userId)
                    .delayElement(Duration.ofSeconds(1))
                    .doOnNext(ReactorMdcUtils.withMdc(data -> {
                        log.info("任务完成: {}", data);
                    }));
        });
    }

    // ==================== 示例 4：虚拟线程 ====================

    /**
     * 示例 4.1：使用虚拟线程池
     * MDC 自动传递
     */
    public void example4_1_VirtualThread() {
        log.info("提交任务到虚拟线程池");
        
        businessVirtualThreadPool.submit(() -> {
            log.info("虚拟线程执行任务");
            // 这里的日志会包含完整的 MDC 信息
            processBusinessLogic();
        });
    }

    /**
     * 示例 4.2：虚拟线程 + CompletableFuture
     * 适合高并发 IO 场景
     */
    public CompletableFuture<String> example4_2_VirtualThreadAsync(Long id) {
        log.info("使用虚拟线程异步处理: id={}", id);
        
        return businessVirtualThreadPool.supplyAsync(() -> {
            log.info("虚拟线程处理中");
            String result = processBusinessLogic();
            log.info("虚拟线程处理完成");
            return result;
        });
    }

    /**
     * 示例 4.3：批量虚拟线程任务
     * 利用虚拟线程的轻量级特性
     */
    public void example4_3_BatchVirtualThreadTasks(List<Long> ids) {
        log.info("批量提交虚拟线程任务，数量: {}", ids.size());
        
        List<CompletableFuture<Void>> futures = ids.stream()
                .map(id -> businessVirtualThreadPool.runAsync(() -> {
                    log.info("处理任务: id={}", id);
                    processBusinessLogic();
                }))
                .toList();
        
        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("所有虚拟线程任务完成");
                });
    }

    // ==================== 示例 5：混合场景 ====================

    /**
     * 示例 5.1：异步线程池 + 响应式编程
     * 复杂业务场景的 MDC 传递
     */
    public Flux<String> example5_1_MixedAsyncAndReactive(List<Long> ids) {
        log.info("混合场景处理，数量: {}", ids.size());
        
        return Flux.fromIterable(ids)
                .flatMap(id -> {
                    // 在响应式流中调用异步线程池
                    CompletableFuture<String> future = new CompletableFuture<>();
                    
                    businessThreadPool.submit(() -> {
                        try {
                            log.info("线程池处理: id={}", id);
                            String result = processOrder(id);
                            future.complete(result);
                        } catch (Exception e) {
                            log.error("处理失败: id={}", id, e);
                            future.completeExceptionally(e);
                        }
                    });
                    
                    return Mono.fromFuture(future);
                })
                .doOnNext(ReactorMdcUtils.withMdc(result -> {
                    log.info("混合场景结果: {}", result);
                }))
                .contextWrite(ReactorMdcUtils.captureMdc());
    }

    /**
     * 示例 5.2：虚拟线程 + 响应式编程
     * 高并发 IO 场景
     */
    public Flux<String> example5_2_MixedVirtualAndReactive(List<Long> ids) {
        log.info("虚拟线程 + 响应式混合场景，数量: {}", ids.size());
        
        return Flux.fromIterable(ids)
                .flatMap(id -> {
                    CompletableFuture<String> future = businessVirtualThreadPool.supplyAsync(() -> {
                        log.info("虚拟线程处理: id={}", id);
                        return "result-" + id;
                    });
                    
                    return Mono.fromFuture(future);
                })
                .doOnNext(ReactorMdcUtils.withMdc(result -> {
                    log.info("虚拟线程混合场景结果: {}", result);
                }))
                .contextWrite(ReactorMdcUtils.captureMdc());
    }

    // ==================== 示例 6：错误处理 ====================

    /**
     * 示例 6.1：异步线程池错误处理
     */
    public void example6_1_AsyncErrorHandling() {
        log.info("演示异步错误处理");
        
        businessThreadPool.submit(() -> {
            try {
                log.info("执行可能失败的任务");
                riskyOperation();
                log.info("任务执行成功");
            } catch (Exception e) {
                log.error("任务执行失败", e);
                // 异常处理逻辑
            }
        });
    }

    /**
     * 示例 6.2：响应式错误处理
     */
    public Flux<String> example6_2_ReactiveErrorHandling() {
        return Flux.range(1, 5)
                .map(i -> {
                    if (i == 3) {
                        throw new RuntimeException("测试异常");
                    }
                    return "value-" + i;
                })
                .doOnError(ReactorMdcUtils.withMdc(error -> {
                    log.error("响应式流错误", error);
                }))
                .onErrorResume(error -> {
                    log.info("从错误中恢复");
                    return Flux.just("fallback-value");
                })
                .contextWrite(ReactorMdcUtils.captureMdc());
    }

    // ==================== 工具方法（模拟业务逻辑）====================

    private String processBusinessLogic() {
        // 模拟业务处理
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "success";
    }

    private void processFile(String file) {
        // 模拟文件处理
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String processOrder(Long orderId) {
        // 模拟订单处理
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "order-" + orderId;
    }

    private Mono<String> processAsync(String data) {
        return Mono.just(data)
                .delayElement(Duration.ofMillis(100))
                .map(d -> "processed-" + d);
    }

    private Flux<String> generateStreamData(String prompt) {
        return Flux.range(1, 100)
                .delayElements(Duration.ofMillis(10))
                .map(i -> "chunk-" + i);
    }

    private String wrapToJson(String chunk) {
        return "{\"data\":\"" + chunk + "\"}";
    }

    private void riskyOperation() {
        // 模拟可能失败的操作
        if (Math.random() > 0.5) {
            throw new RuntimeException("操作失败");
        }
    }
}

