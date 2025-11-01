package com.woopsion.woopsionaicodemother.constant;

/**
 * MDC 常量
 * 
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
public interface MdcConstant {
    
    /**
     * 用户ID
     */
    String USER_ID = "userId";
    
    /**
     * 链路追踪ID
     */
    String TRACE_ID = "traceId";
    
    /**
     * 客户端IP
     */
    String CLIENT_IP = "clientIP";
    
    /**
     * 语言
     */
    String LANG = "lang";
    
    /**
     * 时区
     */
    String ZONE = "zone";
    
    /**
     * Reactor Context 中存储 MDC 的键
     */
    String REACTOR_CONTEXT_MDC_KEY = "MDC_CONTEXT_MAP";
}

