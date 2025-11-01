package com.woopsion.woopsionaicodemother.utils;

import com.woopsion.woopsionaicodemother.constant.MdcConstant;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * MDC 工具类
 * 提供 MDC 的获取、设置、复制、清理等功能
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public class MdcUtils {

    /**
     * 获取当前线程的 MDC 上下文快照
     *
     * @return MDC 上下文 Map，如果为空返回空 Map
     */
    public static Map<String, String> getCopyOfContextMap() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap != null ? new HashMap<>(contextMap) : new HashMap<>();
    }

    /**
     * 设置 MDC 上下文
     *
     * @param contextMap MDC 上下文 Map
     */
    public static void setContextMap(Map<String, String> contextMap) {
        if (contextMap != null && !contextMap.isEmpty()) {
            MDC.setContextMap(contextMap);
        }
    }

    /**
     * 清理当前线程的 MDC
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 生成并设置 TraceId
     *
     * @return 生成的 TraceId
     */
    public static String generateAndSetTraceId() {
        String traceId = generateTraceId();
        MDC.put(MdcConstant.TRACE_ID, traceId);
        return traceId;
    }

    /**
     * 生成 TraceId
     *
     * @return TraceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        if (userId != null) {
            MDC.put(MdcConstant.USER_ID, String.valueOf(userId));
        }
    }

    /**
     * 设置客户端IP
     *
     * @param clientIp 客户端IP
     */
    public static void setClientIp(String clientIp) {
        if (clientIp != null) {
            MDC.put(MdcConstant.CLIENT_IP, clientIp);
        }
    }

    /**
     * 设置语言
     *
     * @param lang 语言
     */
    public static void setLang(String lang) {
        if (lang != null) {
            MDC.put(MdcConstant.LANG, lang);
        }
    }

    /**
     * 设置时区
     *
     * @param zone 时区
     */
    public static void setZone(String zone) {
        if (zone != null) {
            MDC.put(MdcConstant.ZONE, zone);
        }
    }

    /**
     * 获取 TraceId
     *
     * @return TraceId
     */
    public static String getTraceId() {
        return MDC.get(MdcConstant.TRACE_ID);
    }

    /**
     * 包装 Runnable，使其能够传递 MDC 上下文
     *
     * @param runnable 原始 Runnable
     * @return 包装后的 Runnable
     */
    public static Runnable wrap(Runnable runnable) {
        Map<String, String> contextMap = getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = getCopyOfContextMap();
            try {
                setContextMap(contextMap);
                runnable.run();
            } finally {
                clear();
                setContextMap(previous);
            }
        };
    }

    /**
     * 包装 Callable，使其能够传递 MDC 上下文
     *
     * @param callable 原始 Callable
     * @param <T>      返回值类型
     * @return 包装后的 Callable
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        Map<String, String> contextMap = getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = getCopyOfContextMap();
            try {
                setContextMap(contextMap);
                return callable.call();
            } finally {
                clear();
                setContextMap(previous);
            }
        };
    }

    /**
     * 在指定的 MDC 上下文中执行 Runnable
     *
     * @param contextMap MDC 上下文
     * @param runnable   要执行的任务
     */
    public static void runWithContext(Map<String, String> contextMap, Runnable runnable) {
        Map<String, String> previous = getCopyOfContextMap();
        try {
            setContextMap(contextMap);
            runnable.run();
        } finally {
            clear();
            setContextMap(previous);
        }
    }

    /**
     * 在指定的 MDC 上下文中执行 Callable
     *
     * @param contextMap MDC 上下文
     * @param callable   要执行的任务
     * @param <T>        返回值类型
     * @return 执行结果
     * @throws Exception 执行异常
     */
    public static <T> T callWithContext(Map<String, String> contextMap, Callable<T> callable) throws Exception {
        Map<String, String> previous = getCopyOfContextMap();
        try {
            setContextMap(contextMap);
            return callable.call();
        } finally {
            clear();
            setContextMap(previous);
        }
    }
}

