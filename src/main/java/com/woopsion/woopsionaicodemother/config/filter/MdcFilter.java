package com.woopsion.woopsionaicodemother.config.filter;

import cn.hutool.core.util.StrUtil;
import com.woopsion.woopsionaicodemother.constant.MdcConstant;
import com.woopsion.woopsionaicodemother.entity.User;
import com.woopsion.woopsionaicodemother.service.UserService;
import com.woopsion.woopsionaicodemother.utils.MdcUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * MDC 过滤器
 * 在请求入口装载 MDC 信息，包括 traceId、userId、clientIP、lang、zone 等
 * 使用过滤器而不是拦截器，因为过滤器执行更早，能覆盖所有请求（包括静态资源）
 * 
 * <p>白名单机制：</p>
 * <ul>
 *   <li>静态资源路径：不装载 MDC（提升性能）</li>
 *   <li>登录注册路径：装载基础 MDC（traceId、IP等），但不装载 userId</li>
 *   <li>API 文档路径：不装载 MDC（knife4j、swagger 等）</li>
 *   <li>其他路径：装载完整 MDC（包括 userId）</li>
 * </ul>
 *
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter implements Filter {

    @Autowired
    private UserService userService;
    
    /**
     * AntPathMatcher 用于路径匹配
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    /**
     * 完全跳过 MDC 装载的路径（静态资源、API 文档等）
     * 这些路径不需要任何日志追踪，直接跳过以提升性能
     */
    private static final List<String> SKIP_MDC_PATHS = Arrays.asList(
            // 静态资源
            "/static/**",
            "/public/**",
            "/resources/**",
            "/webjars/**",
            "/*.ico",
            "/*.html",
            "/*.js",
            "/*.css",
            "/*.png",
            "/*.jpg",
            "/*.jpeg",
            "/*.gif",
            "/*.svg",
            "/*.woff",
            "/*.woff2",
            "/*.ttf",
            "/*.eot",
            
            // API 文档相关（knife4j、swagger）
            "/api/doc.html/**",
            "/doc.html/**",
            "/swagger-ui.html/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v2/api-docs/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico"
    );
    
    /**
     * 基础 MDC 路径（登录、注册等）
     * 这些路径装载基础 MDC（traceId、IP、lang、zone），但不装载 userId
     */
    private static final List<String> BASIC_MDC_PATHS = Arrays.asList(
            "/user/login",
            "/user/register",
            "/user/logout"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String requestURI = httpRequest.getRequestURI();
                
                // 1. 判断是否跳过 MDC 装载（静态资源、API 文档等）
                if (shouldSkipMdc(requestURI)) {
                    log.trace("跳过 MDC 装载: {}", requestURI);
                    chain.doFilter(request, response);
                    return;
                }
                
                // 2. 装载基础 MDC 信息（所有非跳过路径都需要）
                setupBasicMdc(httpRequest);
                
                // 3. 判断是否需要装载用户 ID
                if (!isBasicMdcPath(requestURI)) {
                    setupUserId(httpRequest);
                }
                
                log.debug("MDC initialized for path: {}", requestURI);
            }

            // 继续执行过滤器链
            chain.doFilter(request, response);
        } finally {
            // 请求结束后清理 MDC，避免内存泄漏
            MdcUtils.clear();
        }
    }
    
    /**
     * 判断是否应该跳过 MDC 装载
     * 
     * @param requestURI 请求 URI
     * @return true 表示跳过，false 表示不跳过
     */
    private boolean shouldSkipMdc(String requestURI) {
        for (String pattern : SKIP_MDC_PATHS) {
            if (pathMatcher.match(pattern, requestURI)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 判断是否为基础 MDC 路径（登录、注册等）
     * 
     * @param requestURI 请求 URI
     * @return true 表示是基础 MDC 路径，false 表示不是
     */
    private boolean isBasicMdcPath(String requestURI) {
        for (String pattern : BASIC_MDC_PATHS) {
            if (pathMatcher.match(pattern, requestURI)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 装载基础 MDC 信息（traceId、clientIP、lang、zone）
     * 
     * @param httpRequest HTTP 请求
     */
    private void setupBasicMdc(HttpServletRequest httpRequest) {
        // 1. 生成并设置 TraceId（用于链路追踪）
        String traceId = httpRequest.getHeader(MdcConstant.TRACE_ID);
        if (StrUtil.isBlank(traceId)) {
            traceId = MdcUtils.generateTraceId();
        }
        MDC.put(MdcConstant.TRACE_ID, traceId);

        // 2. 获取并设置客户端 IP
        String clientIp = getClientIp(httpRequest);
        MdcUtils.setClientIp(clientIp);

        // 3. 从请求头获取 lang 和 zone
        String lang = httpRequest.getHeader(MdcConstant.LANG);
        String zone = httpRequest.getHeader(MdcConstant.ZONE);
        MdcUtils.setLang(lang);
        MdcUtils.setZone(zone);
        
        log.trace("基础 MDC 装载完成: traceId={}, clientIp={}, lang={}, zone={}", 
                traceId, clientIp, lang, zone);
    }
    
    /**
     * 装载用户 ID 到 MDC
     * 对于未登录用户，不抛异常，仅记录 NULL
     * 
     * @param httpRequest HTTP 请求
     */
    private void setupUserId(HttpServletRequest httpRequest) {
        try {
            User loginUser = userService.getLoginUser(httpRequest);
            if (loginUser != null && loginUser.getId() != null) {
                String userId = loginUser.getId().toString();
                MDC.put(MdcConstant.USER_ID, userId);
                log.trace("用户 ID 装载完成: userId={}", userId);
            } else {
                // 未登录用户，设置为 NULL（Logback 配置中会显示为 NULL）
                log.trace("未登录用户，userId 设置为 NULL");
            }
        } catch (Exception e) {
            // 获取用户信息失败（可能未登录或 token 无效），不影响请求继续执行
            // MDC 中的 userId 会显示为 NULL
            log.trace("获取用户信息失败，userId 设置为 NULL: {}", e.getMessage());
        }
    }

    /**
     * 获取客户端真实 IP 地址
     * 考虑了代理、负载均衡等场景
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个 IP 值，第一个才是真实 IP
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            }
            return ip;
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("HTTP_CLIENT_IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        return request.getRemoteAddr();
    }
}

