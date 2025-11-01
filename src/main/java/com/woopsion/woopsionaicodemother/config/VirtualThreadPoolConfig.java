package com.woopsion.woopsionaicodemother.config;

import com.woopsion.woopsionaicodemother.utils.VirtualThreadExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟线程池配置
 * 提供预配置的虚拟线程池和监控功能
 * 
 * <p>虚拟线程优势：</p>
 * <ul>
 *   <li>轻量级：创建成本极低，可以创建数百万个</li>
 *   <li>高效：阻塞操作不会占用平台线程</li>
 *   <li>适合：IO 密集型、高并发场景</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @Autowired
 * @Qualifier("businessVirtualThreadPool")
 * private VirtualThreadExecutor virtualThreadPool;
 * 
 * // 提交任务
 * virtualThreadPool.submit(() -> {
 *     log.info("虚拟线程执行任务");
 *     // 执行业务逻辑
 * });
 * 
 * // 使用 CompletableFuture
 * CompletableFuture<String> future = virtualThreadPool.supplyAsync(() -> {
 *     return "result";
 * });
 * }
 * </pre>
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Slf4j
@Configuration
@EnableScheduling
public class VirtualThreadPoolConfig {

    /**
     * 所有虚拟线程池的注册表，用于监控
     */
    private static final Map<String, VirtualThreadExecutor> EXECUTOR_REGISTRY = new ConcurrentHashMap<>();

    /**
     * 业务虚拟线程池
     * 用于处理一般业务逻辑
     *
     * @return 业务虚拟线程池
     */
    @Bean("businessVirtualThreadPool")
    public VirtualThreadExecutor businessVirtualThreadPool() {
        VirtualThreadExecutor executor = VirtualThreadExecutor.create("business-vt");
        registerExecutor("businessVirtualThreadPool", executor);
        return executor;
    }

    /**
     * IO 虚拟线程池
     * 用于处理 IO 密集型任务（文件读写、网络请求等）
     *
     * @return IO 虚拟线程池
     */
    @Bean("ioVirtualThreadPool")
    public VirtualThreadExecutor ioVirtualThreadPool() {
        VirtualThreadExecutor executor = VirtualThreadExecutor.create("io-vt");
        registerExecutor("ioVirtualThreadPool", executor);
        return executor;
    }

    /**
     * 异步处理虚拟线程池
     * 用于异步消息处理、事件处理等
     *
     * @return 异步处理虚拟线程池
     */
    @Bean("asyncVirtualThreadPool")
    public VirtualThreadExecutor asyncVirtualThreadPool() {
        VirtualThreadExecutor executor = VirtualThreadExecutor.create("async-vt");
        registerExecutor("asyncVirtualThreadPool", executor);
        return executor;
    }

    /**
     * 注册执行器到注册表
     *
     * @param name     执行器名称
     * @param executor 执行器实例
     */
    private void registerExecutor(String name, VirtualThreadExecutor executor) {
        EXECUTOR_REGISTRY.put(name, executor);
        log.info("虚拟线程池已注册: name={}", name);
    }

    /**
     * 获取所有虚拟线程池
     *
     * @return 虚拟线程池映射
     */
    public static Map<String, VirtualThreadExecutor> getAllExecutors() {
        return new ConcurrentHashMap<>(EXECUTOR_REGISTRY);
    }

    /**
     * 获取虚拟线程池监控信息
     *
     * @return 监控信息字符串
     */
    public static String getMonitorInfo() {
        StringBuilder info = new StringBuilder("虚拟线程池监控信息:\n");
        EXECUTOR_REGISTRY.forEach((name, executor) -> {
            info.append(String.format("  [%s] %s\n", name, executor.getStatistics()));
        });
        return info.toString();
    }

    /**
     * 定时打印虚拟线程池监控信息
     * 每5分钟打印一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void printMonitorInfo() {
        if (EXECUTOR_REGISTRY.isEmpty()) {
            return;
        }
        
        log.info("\n{}", getMonitorInfo());
    }

    /**
     * 创建自定义虚拟线程池的工厂方法
     * 
     * 使用示例：
     * <pre>
     * {@code
     * VirtualThreadExecutor customPool = VirtualThreadPoolConfig.createCustomVirtualThreadPool("custom-business");
     * 
     * customPool.submit(() -> {
     *     log.info("执行自定义业务");
     * });
     * }
     * </pre>
     *
     * @param name 线程池名称
     * @return 虚拟线程执行器
     */
    public static VirtualThreadExecutor createCustomVirtualThreadPool(String name) {
        VirtualThreadExecutor executor = VirtualThreadExecutor.create(name);
        EXECUTOR_REGISTRY.put(name, executor);
        log.info("创建自定义虚拟线程池: name={}", name);
        return executor;
    }

    /**
     * 关闭所有虚拟线程池
     * 通常在应用关闭时调用
     */
    public static void shutdownAll() {
        log.info("开始关闭所有虚拟线程池...");
        EXECUTOR_REGISTRY.forEach((name, executor) -> {
            try {
                executor.shutdown();
                log.info("虚拟线程池已关闭: name={}", name);
            } catch (Exception e) {
                log.error("关闭虚拟线程池失败: name={}", name, e);
            }
        });
        EXECUTOR_REGISTRY.clear();
        log.info("所有虚拟线程池已关闭");
    }
}

