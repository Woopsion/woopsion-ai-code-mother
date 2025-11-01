package com.woopsion.woopsionaicodemother.utils;

import com.woopsion.woopsionaicodemother.constant.MdcConstant;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reactor MDC 工具类
 * 提供响应式编程中的 MDC 传递工具
 * 
 * <p>Reactor 响应式编程的特点：</p>
 * <ul>
 *   <li>操作符可能在不同的线程中执行</li>
 *   <li>传统的 ThreadLocal（MDC）无法直接传递</li>
 *   <li>Reactor 提供了 Context API 用于上下文传递</li>
 * </ul>
 * 
 * <p>设计原则：</p>
 * <ul>
 *   <li>不自动传递 MDC，避免性能损耗</li>
 *   <li>由调用者选择性地传递 MDC</li>
 *   <li>提供简洁的 API，使用方便</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * // 1. 在 Flux/Mono 创建时捕获 MDC
 * Flux<String> flux = Flux.just("a", "b", "c")
 *     .contextWrite(ReactorMdcUtils.captureMdc());
 * 
 * // 2. 在需要日志的操作符中恢复 MDC
 * flux.doOnNext(ReactorMdcUtils.withMdc(value -> {
 *     log.info("处理数据: {}", value);  // 这里的日志会包含 MDC 信息
 * }))
 * .subscribe();
 * 
 * // 3. 对于高频操作（如 map），可以不传递 MDC，提升性能
 * flux.map(String::toUpperCase)  // 这里不传递 MDC，性能更好
 *     .doOnNext(ReactorMdcUtils.withMdc(value -> {
 *         log.info("转换后: {}", value);  // 只在需要日志的地方传递 MDC
 *     }))
 *     .subscribe();
 * }
 * </pre>
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public class ReactorMdcUtils {

    /**
     * 捕获当前线程的 MDC 并写入 Reactor Context
     * 通常在 Flux/Mono 创建时调用一次即可
     * 
     * 使用示例：
     * <pre>
     * {@code
     * Flux<String> flux = Flux.just("a", "b", "c")
     *     .contextWrite(ReactorMdcUtils.captureMdc());
     * }
     * </pre>
     *
     * @return Context 写入函数
     */
    public static Function<Context, Context> captureMdc() {
        Map<String, String> contextMap = MdcUtils.getCopyOfContextMap();
        return context -> context.put(MdcConstant.REACTOR_CONTEXT_MDC_KEY, contextMap);
    }

    /**
     * 从 Reactor Context 中恢复 MDC 到当前线程
     * 仅在当前操作中有效
     *
     * @param context Reactor Context
     */
    public static void restoreMdc(reactor.util.context.ContextView context) {
        if (!context.hasKey(MdcConstant.REACTOR_CONTEXT_MDC_KEY)) {
            return;
        }
        
        Map<String, String> contextMap = context.get(MdcConstant.REACTOR_CONTEXT_MDC_KEY);
        MdcUtils.setContextMap(contextMap);
    }

    /**
     * 包装 Consumer，在执行时从 Reactor Context 恢复 MDC
     * 适用于 doOnNext、doOnError 等操作符
     * 
     * 注意：必须配合 contextWrite(captureMdc()) 使用
     * 
     * 使用示例：
     * <pre>
     * {@code
     * flux.doOnNext(ReactorMdcUtils.withMdc(value -> {
     *     log.info("处理数据: {}", value);
     * }))
     * .contextWrite(ReactorMdcUtils.captureMdc());
     * }
     * </pre>
     *
     * @param consumer 原始 Consumer
     * @param <T>      数据类型
     * @return 包装后的 Consumer
     */
    public static <T> Consumer<T> withMdc(Consumer<T> consumer) {
        // 在订阅时捕获 MDC
        Map<String, String> capturedMdc = MdcUtils.getCopyOfContextMap();
        return value -> {
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                // 恢复捕获的 MDC
                MdcUtils.setContextMap(capturedMdc);
                consumer.accept(value);
            } finally {
                MDC.clear();
                MdcUtils.setContextMap(previous);
            }
        };
    }

    /**
     * 包装 Runnable，在执行时从 Reactor Context 恢复 MDC
     * 适用于 doOnComplete、doOnCancel、doOnTerminate 等操作符
     * 
     * 注意：必须配合 contextWrite(captureMdc()) 使用
     * 
     * 使用示例：
     * <pre>
     * {@code
     * flux.doOnComplete(ReactorMdcUtils.withMdc(() -> {
     *     log.info("处理完成");
     * }))
     * .contextWrite(ReactorMdcUtils.captureMdc());
     * }
     * </pre>
     *
     * @param runnable 原始 Runnable
     * @return 包装后的 Runnable
     */
    public static Runnable withMdc(Runnable runnable) {
        // 在订阅时捕获 MDC
        Map<String, String> capturedMdc = MdcUtils.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                // 恢复捕获的 MDC
                MdcUtils.setContextMap(capturedMdc);
                runnable.run();
            } finally {
                MDC.clear();
                MdcUtils.setContextMap(previous);
            }
        };
    }

    /**
     * 包装 Function，在执行时恢复 MDC
     * 适用于 map、flatMap 等操作符
     * 
     * 注意：map 等高频操作不建议使用，会影响性能
     * 
     * 使用示例：
     * <pre>
     * {@code
     * // 不推荐：在高频 map 中使用
     * flux.map(ReactorMdcUtils.withMdc(value -> {
     *     log.info("转换数据: {}", value);
     *     return value.toUpperCase();
     * }))
     * 
     * // 推荐：在 flatMap 中使用（执行频率较低时）
     * flux.flatMap(ReactorMdcUtils.withMdc(value -> {
     *     log.info("处理数据: {}", value);
     *     return externalService.process(value);
     * }))
     * }
     * </pre>
     *
     * @param function 原始 Function
     * @param <T>      输入类型
     * @param <R>      输出类型
     * @return 包装后的 Function
     */
    public static <T, R> Function<T, R> withMdc(Function<T, R> function) {
        return value -> {
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                return function.apply(value);
            } finally {
                MDC.clear();
                MdcUtils.setContextMap(previous);
            }
        };
    }

    /**
     * 在指定的 Reactor Context 中执行任务，恢复 MDC
     *
     * @param context  Reactor Context
     * @param runnable 要执行的任务
     */
    public static void withMdcContext(reactor.util.context.ContextView context, Runnable runnable) {
        Map<String, String> previous = MdcUtils.getCopyOfContextMap();
        try {
            restoreMdc(context);
            runnable.run();
        } finally {
            MDC.clear();
            MdcUtils.setContextMap(previous);
        }
    }

    /**
     * 在指定的 Reactor Context 中执行任务，恢复 MDC，并返回结果
     *
     * @param context  Reactor Context
     * @param supplier 要执行的任务
     * @param <T>      返回值类型
     * @return 执行结果
     */
    public static <T> T withMdcContext(reactor.util.context.ContextView context, java.util.function.Supplier<T> supplier) {
        Map<String, String> previous = MdcUtils.getCopyOfContextMap();
        try {
            restoreMdc(context);
            return supplier.get();
        } finally {
            MDC.clear();
            MdcUtils.setContextMap(previous);
        }
    }

    /**
     * 装饰 Flux，在订阅时捕获 MDC
     * 
     * 使用示例：
     * <pre>
     * {@code
     * public Flux<String> getData() {
     *     return ReactorMdcUtils.deferFluxWithMdc(() -> 
     *         Flux.just("a", "b", "c")
     *             .doOnNext(ReactorMdcUtils.withMdc(value -> {
     *                 log.info("数据: {}", value);
     *             }))
     *     );
     * }
     * }
     * </pre>
     *
     * @param supplier Flux 提供者
     * @param <T>      数据类型
     * @return 装饰后的 Flux
     */
    public static <T> Flux<T> deferFluxWithMdc(java.util.function.Supplier<Flux<T>> supplier) {
        return Flux.deferContextual(context -> {
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                restoreMdc(context);
                return supplier.get();
            } finally {
                MDC.clear();
                MdcUtils.setContextMap(previous);
            }
        }).contextWrite(captureMdc());
    }

    /**
     * 装饰 Mono，在订阅时捕获 MDC
     * 
     * 使用示例：
     * <pre>
     * {@code
     * public Mono<String> getData() {
     *     return ReactorMdcUtils.deferMonoWithMdc(() -> 
     *         Mono.just("data")
     *             .doOnNext(ReactorMdcUtils.withMdc(value -> {
     *                 log.info("数据: {}", value);
     *             }))
     *     );
     * }
     * }
     * </pre>
     *
     * @param supplier Mono 提供者
     * @param <T>      数据类型
     * @return 装饰后的 Mono
     */
    public static <T> Mono<T> deferMonoWithMdc(java.util.function.Supplier<Mono<T>> supplier) {
        return Mono.deferContextual(context -> {
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                restoreMdc(context);
                return supplier.get();
            } finally {
                MDC.clear();
                MdcUtils.setContextMap(previous);
            }
        }).contextWrite(captureMdc());
    }

    /**
     * 为 Flux 添加 MDC 支持的通用方法
     * 在每个元素发射时自动恢复 MDC（性能开销较大，慎用）
     * 
     * 注意：此方法会在每个元素发射时都恢复 MDC，性能开销较大
     * 仅在必要时使用，例如整个流程都需要打印日志的场景
     * 
     * 使用示例：
     * <pre>
     * {@code
     * Flux<String> flux = Flux.just("a", "b", "c");
     * 
     * // 不推荐：为整个 Flux 添加 MDC（每个元素都会复制 MDC）
     * ReactorMdcUtils.withMdcFlux(flux)
     *     .map(String::toUpperCase)
     *     .subscribe();
     * 
     * // 推荐：仅在需要的地方使用 MDC
     * flux.map(String::toUpperCase)
     *     .doOnNext(ReactorMdcUtils.withMdc(value -> {
     *         log.info("数据: {}", value);
     *     }))
     *     .subscribe();
     * }
     * </pre>
     *
     * @param flux 原始 Flux
     * @param <T>  数据类型
     * @return 装饰后的 Flux
     */
    public static <T> Flux<T> withMdcFlux(Flux<T> flux) {
        return flux.doOnEach(signal -> {
            if (!signal.isOnNext()) {
                return;
            }
            try {
                restoreMdc(signal.getContextView());
            } catch (Exception ignored) {
            }
        }).contextWrite(captureMdc());
    }

    /**
     * 为 Mono 添加 MDC 支持的通用方法
     * 
     * 使用示例：
     * <pre>
     * {@code
     * Mono<String> mono = Mono.just("data");
     * 
     * ReactorMdcUtils.withMdcMono(mono)
     *     .doOnNext(value -> log.info("数据: {}", value))
     *     .subscribe();
     * }
     * </pre>
     *
     * @param mono 原始 Mono
     * @param <T>  数据类型
     * @return 装饰后的 Mono
     */
    public static <T> Mono<T> withMdcMono(Mono<T> mono) {
        return mono.doOnEach(signal -> {
            if (!signal.isOnNext()) {
                return;
            }
            try {
                restoreMdc(signal.getContextView());
            } catch (Exception ignored) {
            }
        }).contextWrite(captureMdc());
    }
}

