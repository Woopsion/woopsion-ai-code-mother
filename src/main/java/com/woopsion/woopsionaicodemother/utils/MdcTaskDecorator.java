package com.woopsion.woopsionaicodemother.utils;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 任务装饰器
 * 用于在异步线程池中传递 MDC 上下文
 * 
 * 工作原理：
 * 1. 在任务提交时（主线程），复制当前线程的 MDC 上下文
 * 2. 在任务执行时（子线程），将复制的 MDC 上下文设置到子线程
 * 3. 任务执行完成后，清理子线程的 MDC 上下文
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 在任务提交时（主线程），获取当前线程的 MDC 上下文快照
        Map<String, String> contextMap = MdcUtils.getCopyOfContextMap();
        
        // 返回包装后的 Runnable
        return () -> {
            // 保存子线程原有的 MDC 上下文（如果有的话）
            Map<String, String> previous = MdcUtils.getCopyOfContextMap();
            try {
                // 在任务执行时（子线程），设置 MDC 上下文
                MdcUtils.setContextMap(contextMap);
                
                // 执行原始任务
                runnable.run();
            } finally {
                // 任务执行完成后，清理 MDC
                MDC.clear();
                
                // 恢复子线程原有的 MDC 上下文
                MdcUtils.setContextMap(previous);
            }
        };
    }
}

