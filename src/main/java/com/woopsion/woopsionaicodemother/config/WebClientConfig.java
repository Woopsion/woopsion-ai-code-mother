package com.woopsion.woopsionaicodemother.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * @author wangpengcan
 * @date 2025/11/3
 * @time 10:40
 * @description
配置 WebClient
 * <p>
 * 使用说明：
 * 1. 默认注入（使用默认配置）：
 *    {@code @Autowired private WebClient webClient;}
 *
 * 2. 指定业务配置注入：
 *    {@code @Autowired @Qualifier("aiServiceWebClient") private WebClient aiServiceWebClient;}
 *    {@code @Autowired @Qualifier("paymentServiceWebClient") private WebClient paymentServiceWebClient;}
 *
 * 3. 所有 WebClient 实例都会自动记录请求和响应日志，不影响数据读取
 * </p>
 */
@Slf4j
@Configuration
public class WebClientConfig {

    /**
     * 默认的 WebClient 实例（Primary Bean）
     * 当没有指定 @Qualifier 时，会自动注入此实例
     *
     * @return 默认配置的 WebClient 实例
     */
    @Primary
    @Bean(name = "defaultWebClient")
    public WebClient defaultWebClient() {
        return createWebClientBuilder(
                "default",
                500,
                Duration.ofSeconds(20),
                60000,
                60,
                60,
                16 * 1024 * 1024
        ).build();
    }

    /**
     * AI 服务专用 WebClient 配置
     * 适用于调用 AI 服务接口，配置较长的超时时间
     *
     * 使用示例：
     * {@code @Autowired @Qualifier("aiServiceWebClient") private WebClient aiServiceWebClient;}
     *
     * @return AI 服务专用 WebClient 实例
     */
    @Bean(name = "aiServiceWebClient")
    public WebClient aiServiceWebClient() {
        return createWebClientBuilder(
                "ai-service",
                200,
                Duration.ofSeconds(30),
                120000, // 连接超时 120秒
                300,    // 读超时 300秒
                300,    // 写超时 300秒
                32 * 1024 * 1024 // 内存限制 32MB
        ).build();
    }

    /**
     * 支付服务专用 WebClient 配置
     * 适用于调用支付接口，配置较短的超时时间和重试机制
     *
     * 使用示例：
     * {@code @Autowired @Qualifier("paymentServiceWebClient") private WebClient paymentServiceWebClient;}
     *
     * @return 支付服务专用 WebClient 实例
     */
    @Bean(name = "paymentServiceWebClient")
    public WebClient paymentServiceWebClient() {
        return createWebClientBuilder(
                "payment-service",
                100,
                Duration.ofSeconds(10),
                30000,  // 连接超时 30秒
                30,     // 读超时 30秒
                30,     // 写超时 30秒
                8 * 1024 * 1024 // 内存限制 8MB
        ).build();
    }

    /**
     * 第三方 API 通用 WebClient 配置
     * 适用于调用外部第三方 API
     *
     * 使用示例：
     * {@code @Autowired @Qualifier("thirdPartyApiWebClient") private WebClient thirdPartyApiWebClient;}
     *
     * @return 第三方 API 专用 WebClient 实例
     */
    @Bean(name = "thirdPartyApiWebClient")
    public WebClient thirdPartyApiWebClient() {
        return createWebClientBuilder(
                "third-party-api",
                300,
                Duration.ofSeconds(15),
                45000,  // 连接超时 45秒
                90,     // 读超时 90秒
                90,     // 写超时 90秒
                16 * 1024 * 1024 // 内存限制 16MB
        ).build();
    }

    /**
     * 创建通用的 WebClient.Builder
     * 统一配置日志拦截器，确保所有 WebClient 实例都有日志记录功能
     *
     * @param poolName           连接池名称
     * @param maxConnections     最大连接数
     * @param maxIdleTime        最大空闲时间
     * @param connectTimeoutMs   连接超时时间（毫秒）
     * @param readTimeoutSeconds 读超时时间（秒）
     * @param writeTimeoutSeconds 写超时时间（秒）
     * @param maxInMemorySize    最大内存大小（字节）
     * @return 配置好的 WebClient.Builder
     */
    private WebClient.Builder createWebClientBuilder(
            String poolName,
            int maxConnections,
            Duration maxIdleTime,
            int connectTimeoutMs,
            int readTimeoutSeconds,
            int writeTimeoutSeconds,
            int maxInMemorySize) {

        // 配置 HTTP 连接池
        ConnectionProvider provider = ConnectionProvider.builder(poolName)
                .maxConnections(maxConnections)
                .maxIdleTime(maxIdleTime)
                .build();

        // 配置 HTTP 客户端
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutSeconds)));

        // 配置 WebClient 构建器，包括 HTTP 连接器、交换策略、请求和响应日志
        return WebClient.builder()
                .filter(logRequest(poolName))
                .filter(logResponse(poolName))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs()
                                .maxInMemorySize(maxInMemorySize))
                        .build());
    }

    /**
     * 请求日志拦截器
     * 记录请求方法和 URL，不影响请求体的读取
     *
     * @param poolName 连接池名称，用于标识不同的 WebClient 实例
     * @return 记录请求日志的 ExchangeFilterFunction
     */
    private ExchangeFilterFunction logRequest(String poolName) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("[{}] WebClient Request: {} {}", poolName, request.method(), request.url());
            return Mono.just(request);
        });
    }

    /**
     * 响应日志拦截器
     * 使用 mutate() 方法创建响应的副本，确保不影响后续的响应体读取
     *
     * 关键点：
     * 1. 使用 response.mutate().build() 创建新的响应对象
     * 2. 原始响应体（body）不会被消费，调用者可以正常读取
     * 3. 只记录状态码等元数据信息
     *
     * @param poolName 连接池名称，用于标识不同的 WebClient 实例
     * @return 记录响应日志的 ExchangeFilterFunction
     */
    private ExchangeFilterFunction logResponse(String poolName) {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.info("[{}] WebClient Response status: {}", poolName, response.statusCode());
            // 使用 mutate() 创建新的响应对象，不消费原始响应体
            // 这样调用者可以正常读取响应数据
            return Mono.just(response.mutate().build());
        });
    }
}
