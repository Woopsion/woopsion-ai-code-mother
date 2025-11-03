package com.woopsion.woopsionaicodemother.exampl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * @author wangpengcan
 * @version 1.0.0
 * @description: WebClient 使用示例
 * @create: 2025/11/3
 * @Copyright © 2025 LitLit AI. All rights reserved.
 */
@Slf4j
@Service
public class WebClientUsageExample {

    private static String baseUrl="http://localhost:8123/api";
    // ============================================
    // 方式 1：使用默认的 WebClient（推荐大多数场景）
    // ============================================
    @Autowired
    private WebClient webClient;

    // ============================================
    // 方式 2：使用 AI 服务专用的 WebClient（适用于 AI 接口调用）
    // ============================================
    @Autowired
    @Qualifier("aiServiceWebClient")
    private WebClient aiServiceWebClient;

    // ============================================
    // 方式 3：使用支付服务专用的 WebClient（适用于支付接口）
    // ============================================
    @Autowired
    @Qualifier("paymentServiceWebClient")
    private WebClient paymentServiceWebClient;

    // ============================================
    // 方式 4：使用第三方 API 专用的 WebClient（适用于外部 API）
    // ============================================
    @Autowired
    @Qualifier("thirdPartyApiWebClient")
    private WebClient thirdPartyApiWebClient;

    /**
     * 示例 1：使用默认 WebClient 发送 GET 请求
     * 日志会自动记录：
     * - [default] WebClient Request: GET http://example.com/api/data
     * - [default] WebClient Response status: 200 OK
     */
    public Mono<String> getDataUsingDefaultClient() {
        return webClient
                .get()
                .uri(baseUrl+"/user/get/login")
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(result -> log.debug("获取到数据: {}", result));
    }

    /**
     * 示例 2：使用默认 WebClient 发送 POST 请求
     * 日志会自动记录请求和响应信息，不影响数据读取
     */
    public Mono<String> postDataUsingDefaultClient(Object requestBody) {
        return webClient
                .post()
                .uri(baseUrl+"/submit")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 示例 3：使用 AI 服务 WebClient 调用 AI 接口
     * 该配置有更长的超时时间和更大的内存限制
     * 日志会显示：[ai-service] WebClient Request/Response
     */
    public Mono<String> callAiService(String prompt) {
        return aiServiceWebClient
                .post()
                .uri(baseUrl+"/generate")
                .bodyValue(new AiRequest(prompt))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.error("AI 服务调用失败", error));
    }

    /**
     * 示例 4：使用支付服务 WebClient 处理支付
     * 该配置有较短的超时时间
     * 日志会显示：[payment-service] WebClient Request/Response
     */
    public Mono<PaymentResponse> processPayment(PaymentRequest paymentRequest) {
        return paymentServiceWebClient
                .post()
                .uri(baseUrl+"/pay")
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(PaymentResponse.class)
                .doOnSuccess(response -> log.info("支付处理成功: {}", response.getTransactionId()))
                .doOnError(error -> log.error("支付处理失败", error));
    }

    /**
     * 示例 5：使用第三方 API WebClient 调用外部服务
     * 日志会显示：[third-party-api] WebClient Request/Response
     */
    public Mono<String> callThirdPartyApi(String endpoint) {
        return thirdPartyApiWebClient
                .get()
                .uri(endpoint)
                .header("Authorization", "Bearer your-token-here")
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(result -> log.debug("第三方 API 返回: {}", result));
    }

    /**
     * 示例 6：使用默认 WebClient，带有错误处理
     * 响应体可以正常读取，不会被日志过滤器消费
     */
    public Mono<ApiResponse> getDataWithErrorHandling() {
        return webClient
                .get()
                .uri(baseUrl+"/resource")
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError(),
                        response -> Mono.error(new RuntimeException("客户端错误"))
                )
                .onStatus(
                        status -> status.is5xxServerError(),
                        response -> Mono.error(new RuntimeException("服务器错误"))
                )
                .bodyToMono(ApiResponse.class);
    }

    /**
     * 示例 7：使用 exchange() 方法获取完整响应
     * 日志过滤器不会影响响应体的读取
     */
    public Mono<String> getFullResponse() {
        return webClient
                .get()
                .uri(baseUrl+"/full")
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class);
                    } else {
                        return Mono.error(new RuntimeException("请求失败: " + response.statusCode()));
                    }
                });
    }

    // ============================================
    // 内部类：示例请求/响应对象
    // ============================================

    /**
     * AI 请求对象示例
     */
    private static class AiRequest {
        private String prompt;

        public AiRequest(String prompt) {
            this.prompt = prompt;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

    /**
     * 支付请求对象示例
     */
    private static class PaymentRequest {
        private String orderId;
        private Double amount;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }
    }

    /**
     * 支付响应对象示例
     */
    private static class PaymentResponse {
        private String transactionId;
        private String status;

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /**
     * API 响应对象示例
     */
    private static class ApiResponse {
        private String data;
        private Integer code;
        private String message;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

