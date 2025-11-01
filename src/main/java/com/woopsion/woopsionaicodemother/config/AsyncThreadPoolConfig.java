package com.woopsion.woopsionaicodemother.config;

import com.woopsion.woopsionaicodemother.utils.MdcTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

/**
 * 异步线程池配置
 * 提供多种预配置的线程池，包含 MDC 传递、监控、拒绝策略等
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @Autowired
 * @Qualifier("businessThreadPool")
 * private ThreadPoolTaskExecutor businessThreadPool;
 * 
 * // 提交任务，MDC 会自动传递
 * businessThreadPool.submit(() -> {
 *     log.info("这里的日志会包含 traceId、userId 等 MDC 信息");
 * });
 * }
 * </pre>
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncThreadPoolConfig {

    /**
     * 业务线程池
     * 用于处理一般业务逻辑，核心线程数较大，适合中等并发
     * 
     * 配置说明：
     * - 核心线程数: 10（根据业务量调整）
     * - 最大线程数: 30
     * - 队列容量: 200
     * - 线程存活时间: 60秒
     * - 拒绝策略: CallerRunsPolicy（由调用线程执行）
     * - MDC 传递: 支持
     *
     * @return 业务线程池
     */
    @Bean("businessThreadPool")
    public ThreadPoolTaskExecutor businessThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：线程池创建时初始化的线程数
        executor.setCorePoolSize(10);
        
        // 最大线程数：线程池最大允许的线程数
        executor.setMaxPoolSize(30);
        
        // 队列容量：用于缓存待执行的任务
        executor.setQueueCapacity(200);
        
        // 线程空闲时间：非核心线程在空闲时的存活时间（秒）
        executor.setKeepAliveSeconds(60);
        
        // 线程名称前缀：便于问题排查和监控
        executor.setThreadNamePrefix("business-pool-");
        
        // 拒绝策略：队列满时，由调用线程执行任务（保证任务不丢失，但会降低调用线程性能）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待终止时间：应用关闭时，等待任务完成的时间（秒）
        executor.setAwaitTerminationSeconds(60);
        
        // 关闭时等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 设置 MDC 装饰器，自动传递 MDC 上下文
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        // 初始化线程池
        executor.initialize();
        
        log.info("业务线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * IO 密集型线程池
     * 用于处理 IO 密集型任务（如文件读写、网络请求等）
     * 
     * 配置说明：
     * - 核心线程数: 50（IO 密集型可以设置更多线程）
     * - 最大线程数: 100
     * - 队列容量: 500
     * - 线程存活时间: 120秒
     * - 拒绝策略: CallerRunsPolicy
     * - MDC 传递: 支持
     *
     * @return IO 密集型线程池
     */
    @Bean("ioThreadPool")
    public ThreadPoolTaskExecutor ioThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("io-pool-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 设置 MDC 装饰器
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        
        log.info("IO线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * CPU 密集型线程池
     * 用于处理 CPU 密集型任务（如计算、数据处理等）
     * 
     * 配置说明：
     * - 核心线程数: CPU核心数 + 1
     * - 最大线程数: CPU核心数 * 2
     * - 队列容量: 100
     * - 线程存活时间: 30秒
     * - 拒绝策略: AbortPolicy（抛异常，快速失败）
     * - MDC 传递: 支持
     *
     * @return CPU 密集型线程池
     */
    @Bean("cpuThreadPool")
    public ThreadPoolTaskExecutor cpuThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        int processors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(processors + 1);
        executor.setMaxPoolSize(processors * 2);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("cpu-pool-");
        
        // CPU 密集型使用 AbortPolicy，快速失败，避免过多任务积压
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setAwaitTerminationSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 设置 MDC 装饰器
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        
        log.info("CPU线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 快速任务线程池
     * 用于处理快速、轻量级的任务
     * 
     * 配置说明：
     * - 核心线程数: 5
     * - 最大线程数: 20
     * - 队列容量: 50（较小，避免任务积压）
     * - 线程存活时间: 30秒
     * - 拒绝策略: DiscardOldestPolicy（丢弃最旧的任务）
     * - MDC 传递: 支持
     *
     * @return 快速任务线程池
     */
    @Bean("fastThreadPool")
    public ThreadPoolTaskExecutor fastThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("fast-pool-");
        
        // 快速任务可以丢弃最旧的任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setAwaitTerminationSeconds(30);
        executor.setWaitForTasksToCompleteOnShutdown(false); // 快速任务不等待完成
        
        // 设置 MDC 装饰器
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        
        log.info("快速任务线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 监控线程池
     * 用于监控和统计任务，优先级较低
     * 
     * 配置说明：
     * - 核心线程数: 2
     * - 最大线程数: 5
     * - 队列容量: 1000（容许较多任务积压）
     * - 线程存活时间: 300秒（5分钟）
     * - 拒绝策略: DiscardPolicy（直接丢弃，监控任务可接受丢失）
     * - 线程优先级: 较低
     * - MDC 传递: 支持
     *
     * @return 监控线程池
     */
    @Bean("monitorThreadPool")
    public ThreadPoolTaskExecutor monitorThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(300);
        executor.setThreadNamePrefix("monitor-pool-");
        
        // 监控任务可以直接丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setAwaitTerminationSeconds(10);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        
        // 设置较低的线程优先级
        executor.setThreadPriority(Thread.NORM_PRIORITY - 1);
        
        // 设置 MDC 装饰器
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        
        log.info("监控线程池初始化完成: coreSize={}, maxSize={}, queueCapacity={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }

    /**
     * 创建自定义线程池的工厂方法
     * 用于业务方根据需要创建自己的线程池
     * 
     * 使用示例：
     * <pre>
     * {@code
     * ThreadPoolTaskExecutor customPool = AsyncThreadPoolConfig.createCustomThreadPool(
     *     "custom-pool-",    // 线程名称前缀
     *     10,                // 核心线程数
     *     20,                // 最大线程数
     *     100,               // 队列容量
     *     60,                // 存活时间（秒）
     *     new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
     * );
     * }
     * </pre>
     *
     * @param threadNamePrefix   线程名称前缀
     * @param corePoolSize       核心线程数
     * @param maxPoolSize        最大线程数
     * @param queueCapacity      队列容量
     * @param keepAliveSeconds   存活时间（秒）
     * @param rejectedHandler    拒绝策略
     * @return 自定义线程池
     */
    public static ThreadPoolTaskExecutor createCustomThreadPool(
            String threadNamePrefix,
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            int keepAliveSeconds,
            RejectedExecutionHandler rejectedHandler) {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(rejectedHandler);
        executor.setAwaitTerminationSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 自动添加 MDC 装饰器
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        
        log.info("自定义线程池初始化完成: threadNamePrefix={}, coreSize={}, maxSize={}, queueCapacity={}", 
                threadNamePrefix, corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }
}

