# WebClient 配置使用指南

## 📖 概述

本配置提供了统一的 WebClient 管理方案，支持：
- ✅ 自动日志记录（请求和响应）
- ✅ 多业务场景配置
- ✅ 默认实例自动注入
- ✅ 不影响响应体读取

## 🎯 核心功能

### 1. 自动日志记录

所有 WebClient 实例都会自动记录请求和响应日志：

```
[default] WebClient Request: GET http://example.com/api/data
[default] WebClient Response status: 200 OK
```

**关键特性：**
- 日志记录不消费响应体
- 调用者可以正常读取响应数据
- 使用 `response.mutate().build()` 确保数据流不被影响

### 2. 多业务配置支持

提供了 4 种预配置的 WebClient 实例：

| Bean 名称 | 适用场景 | 连接数 | 超时时间 | 内存限制 |
|-----------|---------|--------|---------|---------|
| `defaultWebClient` | 通用场景（默认） | 500 | 60秒 | 16MB |
| `aiServiceWebClient` | AI 服务调用 | 200 | 300秒 | 32MB |
| `paymentServiceWebClient` | 支付接口 | 100 | 30秒 | 8MB |
| `thirdPartyApiWebClient` | 第三方 API | 300 | 90秒 | 16MB |

## 📝 使用方法

### 方式 1：使用默认 WebClient（推荐）

```java
@Service
public class MyService {
    @Autowired
    private WebClient webClient;  // 自动注入默认配置
    
    public Mono<String> getData() {
        return webClient
            .get()
            .uri("http://example.com/api/data")
            .retrieve()
            .bodyToMono(String.class);
    }
}
```

**日志输出：**
```
[default] WebClient Request: GET http://example.com/api/data
[default] WebClient Response status: 200 OK
```

### 方式 2：使用指定业务配置

#### AI 服务调用（长超时）

```java
@Service
public class AiService {
    @Autowired
    @Qualifier("aiServiceWebClient")
    private WebClient aiServiceWebClient;
    
    public Mono<String> generateContent(String prompt) {
        return aiServiceWebClient
            .post()
            .uri("http://ai-service.com/api/generate")
            .bodyValue(new AiRequest(prompt))
            .retrieve()
            .bodyToMono(String.class);
    }
}
```

**日志输出：**
```
[ai-service] WebClient Request: POST http://ai-service.com/api/generate
[ai-service] WebClient Response status: 200 OK
```

#### 支付服务调用（短超时）

```java
@Service
public class PaymentService {
    @Autowired
    @Qualifier("paymentServiceWebClient")
    private WebClient paymentServiceWebClient;
    
    public Mono<PaymentResponse> pay(PaymentRequest request) {
        return paymentServiceWebClient
            .post()
            .uri("http://payment.com/api/pay")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PaymentResponse.class);
    }
}
```

**日志输出：**
```
[payment-service] WebClient Request: POST http://payment.com/api/pay
[payment-service] WebClient Response status: 200 OK
```

#### 第三方 API 调用

```java
@Service
public class ThirdPartyService {
    @Autowired
    @Qualifier("thirdPartyApiWebClient")
    private WebClient thirdPartyApiWebClient;
    
    public Mono<String> callExternalApi(String endpoint) {
        return thirdPartyApiWebClient
            .get()
            .uri(endpoint)
            .header("Authorization", "Bearer token")
            .retrieve()
            .bodyToMono(String.class);
    }
}
```

**日志输出：**
```
[third-party-api] WebClient Request: GET http://external-api.com/data
[third-party-api] WebClient Response status: 200 OK
```

## 🔍 高级用法

### 1. 带错误处理的请求

```java
public Mono<ApiResponse> getDataWithErrorHandling() {
    return webClient
        .get()
        .uri("http://example.com/api/resource")
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
```

### 2. 使用 exchange() 获取完整响应

```java
public Mono<String> getFullResponse() {
    return webClient
        .get()
        .uri("http://example.com/api/full")
        .exchangeToMono(response -> {
            if (response.statusCode().is2xxSuccessful()) {
                return response.bodyToMono(String.class);
            } else {
                return Mono.error(new RuntimeException("请求失败"));
            }
        });
}
```

### 3. POST 请求

```java
public Mono<String> postData(MyRequest request) {
    return webClient
        .post()
        .uri("http://example.com/api/submit")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(String.class);
}
```

## ⚙️ 配置说明

### 连接池配置

每个 WebClient 都有独立的连接池，可根据业务特点调整：

```java
ConnectionProvider provider = ConnectionProvider.builder("pool-name")
    .maxConnections(500)        // 最大连接数
    .maxIdleTime(Duration.ofSeconds(20))  // 最大空闲时间
    .build();
```

### 超时配置

三种超时时间可独立设置：

```java
HttpClient httpClient = HttpClient.create(provider)
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60000)  // 连接超时
    .responseTimeout(Duration.ofSeconds(60))               // 响应超时
    .doOnConnected(conn -> conn
        .addHandlerLast(new ReadTimeoutHandler(60))        // 读超时
        .addHandlerLast(new WriteTimeoutHandler(60)));     // 写超时
```

### 内存限制配置

防止大响应占用过多内存：

```java
.exchangeStrategies(ExchangeStrategies.builder()
    .codecs(configurer -> configurer.defaultCodecs()
        .maxInMemorySize(16 * 1024 * 1024))  // 16MB
    .build());
```

## 📊 日志实现原理

### 请求日志

```java
private ExchangeFilterFunction logRequest(String poolName) {
    return ExchangeFilterFunction.ofRequestProcessor(request -> {
        log.info("[{}] WebClient Request: {} {}", poolName, request.method(), request.url());
        return Mono.just(request);  // 返回原始请求，不做修改
    });
}
```

### 响应日志（关键！）

```java
private ExchangeFilterFunction logResponse(String poolName) {
    return ExchangeFilterFunction.ofResponseProcessor(response -> {
        log.info("[{}] WebClient Response status: {}", poolName, response.statusCode());
        // 使用 mutate() 创建新的响应对象，不消费原始响应体
        return Mono.just(response.mutate().build());
    });
}
```

**为什么要使用 `response.mutate().build()`？**

- ❌ 直接返回 `response`：可能在某些情况下工作，但不够安全
- ❌ 读取 `response.bodyToMono()`：会消费响应体，调用者无法再次读取
- ✅ 使用 `response.mutate().build()`：创建新的响应对象，保持原始数据流不变

## 🎨 自定义配置

如果需要添加新的业务配置：

```java
@Bean(name = "customWebClient")
public WebClient customWebClient() {
    return createWebClientBuilder(
        "custom-name",           // 日志标识
        300,                     // 最大连接数
        Duration.ofSeconds(15),  // 最大空闲时间
        45000,                   // 连接超时（毫秒）
        90,                      // 读超时（秒）
        90,                      // 写超时（秒）
        16 * 1024 * 1024        // 内存限制（字节）
    ).build();
}
```

使用：

```java
@Autowired
@Qualifier("customWebClient")
private WebClient customWebClient;
```

## ⚠️ 注意事项

1. **默认注入**：不使用 `@Qualifier` 时，会自动注入 `defaultWebClient`
2. **日志级别**：确保日志配置中 `INFO` 级别已开启
3. **响应体读取**：日志过滤器不会影响响应体的读取，可以放心使用
4. **超时配置**：根据业务特点选择合适的 WebClient 实例
5. **连接池隔离**：不同业务使用不同的 WebClient 实例，连接池相互隔离

## 📚 参考文档

- [Spring WebClient 官方文档](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)
- [Project Reactor 文档](https://projectreactor.io/docs/core/release/reference/)
- [Netty 配置指南](https://netty.io/wiki/user-guide-for-4.x.html)

## 🔄 版本历史

- **v1.0.0** (2025-11-03)
  - 初始版本
  - 支持自动日志记录
  - 提供 4 种预配置实例
  - 确保响应体不被日志过滤器消费

