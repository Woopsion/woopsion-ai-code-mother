# MDC（Mapped Diagnostic Context）使用指南

## 目录
- [概述](#概述)
- [核心组件](#核心组件)
- [使用场景](#使用场景)
  - [1. 普通同步代码](#1-普通同步代码)
  - [2. 异步线程池](#2-异步线程池)
  - [3. 响应式编程（Reactor）](#3-响应式编程reactor)
  - [4. 虚拟线程](#4-虚拟线程)
- [最佳实践](#最佳实践)
- [性能优化](#性能优化)
- [监控与排查](#监控与排查)

---

## 概述

本项目实现了一套完整的 MDC 管理方案，支持在多种并发场景下传递日志上下文信息（如 `traceId`、`userId`、`clientIP`、`lang`、`zone` 等），确保日志输出符合统一格式。

### 日志格式

```
[userId:1001] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123def456]  [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  INFO  c.w.w.controller.AppController - 处理请求成功
```

### 核心优势

- ✅ **自动装载**：请求入口自动装载 MDC 信息
- ✅ **无缝传递**：支持异步线程池、响应式编程、虚拟线程
- ✅ **高性能**：可选择性传递，避免不必要的性能开销
- ✅ **易用性**：调用方无需过多关心细节，API 简洁明了
- ✅ **可监控**：提供线程池监控和统计功能

---

## 核心组件

### 1. MDC 常量类

`MdcConstant.java` - 定义 MDC 键名常量

```java
public interface MdcConstant {
    String USER_ID = "userId";
    String TRACE_ID = "traceId";
    String CLIENT_IP = "clientIP";
    String LANG = "lang";
    String ZONE = "zone";
    String REACTOR_CONTEXT_MDC_KEY = "MDC_CONTEXT_MAP";
}
```

### 2. MDC 工具类

`MdcUtils.java` - 提供 MDC 的获取、设置、复制、清理等功能

### 3. MDC 过滤器

`MdcFilter.java` - 在请求入口自动装载 MDC 信息

### 4. 异步线程池配置

`AsyncThreadPoolConfig.java` - 提供多种预配置的线程池，支持 MDC 传递

### 5. Reactor MDC 工具

`ReactorMdcUtils.java` - 响应式编程中的 MDC 传递工具

### 6. 虚拟线程执行器

`VirtualThreadExecutor.java` - 虚拟线程池封装，支持 MDC 传递

---

## 白名单配置

### 白名单机制概述

`MdcFilter` 支持基于 URL 的白名单机制，提供三级过滤策略：

| 级别 | MDC 装载内容 | 典型场景 | 性能 |
|-----|------------|---------|------|
| **完全跳过** | 无 | 静态资源、API 文档 | ⚡⚡⚡ 最快 |
| **基础 MDC** | traceId、IP、lang、zone | 登录、注册 | ⚡⚡ 较快 |
| **完整 MDC** | traceId、userId、IP、lang、zone | 业务接口 | ⚡ 正常 |

### 默认白名单配置

#### 1. 完全跳过 MDC 的路径

```java
// 静态资源
"/static/**", "/public/**", "/resources/**", "/webjars/**"
"/*.ico", "/*.html", "/*.js", "/*.css", "/*.png", "/*.jpg", "/*.gif", "/*.svg"
"/*.woff", "/*.woff2", "/*.ttf", "/*.eot"

// API 文档（knife4j、swagger）
"/api/doc.html/**", "/doc.html/**"
"/swagger-ui.html/**", "/swagger-ui/**", "/swagger-resources/**"
"/v2/api-docs/**", "/v3/api-docs/**"
```

#### 2. 基础 MDC 路径（不装载 userId）

```java
"/user/login"      // 用户登录
"/user/register"   // 用户注册
"/user/logout"     // 用户登出
```

#### 3. 完整 MDC 路径

除上述路径外的所有路径，都装载完整 MDC（包括 userId）。

### 自定义白名单

修改 `MdcFilter.java` 中的常量列表：

```java
// 添加新的跳过路径
private static final List<String> SKIP_MDC_PATHS = Arrays.asList(
    // 原有配置...
    
    // 自定义：健康检查
    "/actuator/**",
    "/health/**",
    
    // 自定义：文件下载
    "/download/**",
    "/export/**"
);

// 添加新的基础 MDC 路径
private static final List<String> BASIC_MDC_PATHS = Arrays.asList(
    // 原有配置...
    
    // 自定义：验证码
    "/captcha/**",
    
    // 自定义：第三方回调
    "/callback/**"
);
```

### 路径匹配规则

| 模式 | 说明 | 示例 |
|-----|------|------|
| `?` | 匹配单个字符 | `/user/?.html` 匹配 `/user/a.html` |
| `*` | 匹配任意字符（不包括 `/`） | `/user/*.html` 匹配 `/user/test.html` |
| `**` | 匹配任意目录和字符 | `/user/**` 匹配 `/user/a/b/c` |

---

## 使用场景

### 1. 普通同步代码

在普通的同步代码中，MDC 会自动在请求入口装载，无需任何额外操作。

```java
@RestController
@RequestMapping("/api")
public class UserController {
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        // MDC 已自动装载，直接打印日志即可
        log.info("查询用户: id={}", id);
        return userService.getUser(id);
    }
}
```

**日志输出：**
```
[userId:1001] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123]  [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  INFO  c.w.w.controller.UserController - 查询用户: id=1001
```

### 2. 异步线程池

#### 2.1 使用预配置的线程池

项目提供了 5 种预配置的线程池，自动支持 MDC 传递：

```java
@Service
public class OrderService {
    
    @Autowired
    @Qualifier("businessThreadPool")
    private ThreadPoolTaskExecutor businessThreadPool;
    
    public void processOrder(Order order) {
        // 提交任务到线程池，MDC 会自动传递
        businessThreadPool.submit(() -> {
            log.info("处理订单: orderId={}", order.getId());
            // 这里的日志会包含完整的 MDC 信息
            // 执行业务逻辑...
        });
    }
}
```

#### 2.2 线程池类型说明

| 线程池名称 | Bean 名称 | 适用场景 | 核心线程数 | 最大线程数 | 队列容量 |
|-----------|----------|---------|-----------|-----------|---------|
| 业务线程池 | businessThreadPool | 一般业务逻辑 | 10 | 30 | 200 |
| IO 线程池 | ioThreadPool | IO 密集型任务 | 50 | 100 | 500 |
| CPU 线程池 | cpuThreadPool | CPU 密集型任务 | CPU核心数+1 | CPU核心数×2 | 100 |
| 快速线程池 | fastThreadPool | 快速、轻量级任务 | 5 | 20 | 50 |
| 监控线程池 | monitorThreadPool | 监控统计任务 | 2 | 5 | 1000 |

#### 2.3 创建自定义线程池

```java
@Configuration
public class CustomThreadPoolConfig {
    
    @Bean("orderProcessThreadPool")
    public ThreadPoolTaskExecutor orderProcessThreadPool() {
        return AsyncThreadPoolConfig.createCustomThreadPool(
            "order-process-",                           // 线程名称前缀
            15,                                         // 核心线程数
            40,                                         // 最大线程数
            150,                                        // 队列容量
            90,                                         // 存活时间（秒）
            new ThreadPoolExecutor.CallerRunsPolicy()   // 拒绝策略
        );
    }
}
```

#### 2.4 拒绝策略说明

| 策略 | 说明 | 适用场景 |
|-----|------|---------|
| CallerRunsPolicy | 由调用线程执行任务 | 业务重要，不能丢失任务 |
| AbortPolicy | 抛出异常 | CPU 密集型，快速失败 |
| DiscardOldestPolicy | 丢弃最旧的任务 | 可接受部分任务丢失 |
| DiscardPolicy | 直接丢弃 | 监控类任务，可丢失 |

### 3. 响应式编程（Reactor）

#### 3.1 基本使用

响应式编程中，由调用者**选择性**传递 MDC，避免性能损耗。

```java
@Service
public class DataService {
    
    public Flux<Data> processData() {
        return Flux.just("a", "b", "c")
                // 1. 在创建 Flux 时捕获 MDC
                .contextWrite(ReactorMdcUtils.captureMdc())
                // 2. 在需要日志的操作符中传递 MDC
                .doOnNext(ReactorMdcUtils.withMdc(value -> {
                    log.info("处理数据: {}", value);  // ✅ 包含 MDC 信息
                }))
                // 3. 高频操作不传递 MDC（性能优化）
                .map(String::toUpperCase)  // ❌ 不传递 MDC，性能更好
                .doOnError(ReactorMdcUtils.withMdc(error -> {
                    log.error("处理失败", error);  // ✅ 包含 MDC 信息
                }));
    }
}
```

#### 3.2 性能优化原则

**✅ 推荐：在低频操作符中传递 MDC**
- `doOnNext` - 需要打印日志时
- `doOnError` - 错误处理
- `doOnComplete` - 完成回调
- `flatMap` - 执行频率较低时

**❌ 不推荐：在高频操作符中传递 MDC**
- `map` - 数据转换（高频）
- `filter` - 数据过滤（高频）
- `reduce` - 聚合操作（高频）

#### 3.3 实际案例：SSE 流式输出

```java
@GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamChat(@RequestParam String message) {
    return chatService.chat(message)
            // ❌ 不在高频 map 中传递 MDC
            .map(chunk -> ServerSentEvent.builder()
                    .data(chunk)
                    .build())
            // ✅ 仅在需要日志的地方传递 MDC
            .doOnError(ReactorMdcUtils.withMdc(error -> {
                log.error("流式传输错误", error);
            }))
            .doOnComplete(ReactorMdcUtils.withMdc(() -> {
                log.info("流式传输完成");
            }))
            // ✅ 捕获 MDC 上下文
            .contextWrite(ReactorMdcUtils.captureMdc());
}
```

#### 3.4 使用 deferWithMdc

对于需要延迟执行的场景，可以使用 `deferWithMdc`：

```java
public Flux<String> getData() {
    return ReactorMdcUtils.deferWithMdc(() -> 
        externalService.fetchData()
                .doOnNext(ReactorMdcUtils.withMdc(data -> {
                    log.info("接收数据: {}", data);
                }))
    );
}
```

### 4. 虚拟线程

#### 4.1 使用预配置的虚拟线程池

虚拟线程适合 IO 密集型、高并发场景，创建成本极低。

```java
@Service
public class FileService {
    
    @Autowired
    @Qualifier("ioVirtualThreadPool")
    private VirtualThreadExecutor ioVirtualThreadPool;
    
    public void processFiles(List<String> files) {
        files.forEach(file -> {
            // 提交任务到虚拟线程池，MDC 自动传递
            ioVirtualThreadPool.submit(() -> {
                log.info("处理文件: {}", file);  // ✅ 包含 MDC 信息
                // 执行文件处理...
            });
        });
    }
}
```

#### 4.2 虚拟线程池类型

| 线程池名称 | Bean 名称 | 适用场景 |
|-----------|----------|---------|
| 业务虚拟线程池 | businessVirtualThreadPool | 一般业务逻辑 |
| IO 虚拟线程池 | ioVirtualThreadPool | IO 密集型任务 |
| 异步虚拟线程池 | asyncVirtualThreadPool | 异步消息处理 |

#### 4.3 使用 CompletableFuture

虚拟线程池提供了 `CompletableFuture` 支持：

```java
public CompletableFuture<String> asyncProcess(Long id) {
    return ioVirtualThreadPool.supplyAsync(() -> {
        log.info("异步处理: id={}", id);  // ✅ 包含 MDC 信息
        return externalService.process(id);
    });
}
```

#### 4.4 创建自定义虚拟线程池

```java
@Service
public class CustomService {
    
    private final VirtualThreadExecutor customVirtualPool;
    
    public CustomService() {
        this.customVirtualPool = VirtualThreadPoolConfig
                .createCustomVirtualThreadPool("custom-business");
    }
    
    public void process() {
        customVirtualPool.submit(() -> {
            log.info("自定义虚拟线程执行");
            // 业务逻辑...
        });
    }
}
```

#### 4.5 虚拟线程监控

系统会定时（每 5 分钟）打印虚拟线程池统计信息：

```
虚拟线程池监控信息:
  [businessVirtualThreadPool] VirtualThreadExecutor[name=business-vt, totalTasks=1234, activeTasks=56]
  [ioVirtualThreadPool] VirtualThreadExecutor[name=io-vt, totalTasks=5678, activeTasks=123]
  [asyncVirtualThreadPool] VirtualThreadExecutor[name=async-vt, totalTasks=890, activeTasks=12]
```

手动获取监控信息：

```java
// 获取所有虚拟线程池
Map<String, VirtualThreadExecutor> executors = VirtualThreadPoolConfig.getAllExecutors();

// 获取监控信息
String info = VirtualThreadPoolConfig.getMonitorInfo();
log.info(info);

// 获取单个线程池统计
String stats = ioVirtualThreadPool.getStatistics();
int activeTasks = ioVirtualThreadPool.getActiveTaskCount();
int totalTasks = ioVirtualThreadPool.getTotalTaskCount();
```

---

## 最佳实践

### 1. 设置用户信息

在登录后，及时设置用户 ID 到 MDC：

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        // 从 token 中解析用户信息
        String token = request.getHeader("Authorization");
        if (token != null) {
            Long userId = parseUserId(token);
            // 设置用户 ID 到 MDC
            MdcUtils.setUserId(userId);
        }
        return true;
    }
}
```

### 2. 选择合适的线程池

根据任务特点选择合适的线程池：

| 任务特点 | 推荐线程池 |
|---------|----------|
| IO 密集型（文件读写、网络请求） | ioThreadPool 或 ioVirtualThreadPool |
| CPU 密集型（计算、加密） | cpuThreadPool |
| 一般业务逻辑 | businessThreadPool 或 businessVirtualThreadPool |
| 快速轻量级任务 | fastThreadPool |
| 监控统计任务 | monitorThreadPool |
| 高并发 IO 场景 | virtualThreadPool（首选） |

### 3. 响应式编程中的 MDC 传递策略

```java
// ✅ 好的做法
Flux.just("data")
    .map(String::toUpperCase)              // 高频操作，不传递 MDC
    .flatMap(this::callExternalService)    // 可能失败，不传递 MDC（在内部处理）
    .doOnNext(ReactorMdcUtils.withMdc(data -> {
        log.info("处理结果: {}", data);     // 需要日志，传递 MDC
    }))
    .contextWrite(ReactorMdcUtils.captureMdc());

// ❌ 不好的做法（性能差）
Flux.just("data")
    .map(ReactorMdcUtils.withMdc(String::toUpperCase))  // 每次都复制 MDC
    .filter(ReactorMdcUtils.withMdc(s -> s.length() > 5))
    // 过度使用 MDC 传递，影响性能
```

### 4. 异常处理

确保异常处理时也能记录 MDC 信息：

```java
// 异步线程池
businessThreadPool.submit(() -> {
    try {
        // 业务逻辑
    } catch (Exception e) {
        log.error("业务处理失败", e);  // ✅ 包含 MDC 信息
    }
});

// 响应式编程
flux.doOnError(ReactorMdcUtils.withMdc(error -> {
    log.error("流处理失败", error);  // ✅ 包含 MDC 信息
}))
```

---

## 性能优化

### 1. 响应式编程性能优化

**问题：** 在高频操作符（如 `map`）中传递 MDC 会导致性能下降。

**解决方案：** 只在需要打印日志的地方传递 MDC。

```java
// 场景：SSE 流式输出大量数据
Flux<String> dataFlux = aiService.generateCode(prompt)
    // ❌ 不好：每个 chunk 都复制 MDC（可能每秒数百次）
    .map(ReactorMdcUtils.withMdc(chunk -> {
        return wrapToJson(chunk);
    }))
    
    // ✅ 好：高频操作不传递 MDC
    .map(chunk -> wrapToJson(chunk))
    // 只在需要日志的地方传递 MDC
    .doOnError(ReactorMdcUtils.withMdc(error -> {
        log.error("生成失败", error);
    }));
```

### 2. 线程池优化

根据业务特点调整线程池参数：

```java
// IO 密集型：增加线程数
ThreadPoolTaskExecutor ioPool = createCustomThreadPool(
    "io-pool-",
    50,   // 核心线程数
    100,  // 最大线程数
    500,  // 队列容量
    120,  // 存活时间
    new ThreadPoolExecutor.CallerRunsPolicy()
);

// CPU 密集型：减少线程数
ThreadPoolTaskExecutor cpuPool = createCustomThreadPool(
    "cpu-pool-",
    Runtime.getRuntime().availableProcessors() + 1,
    Runtime.getRuntime().availableProcessors() * 2,
    100,
    30,
    new ThreadPoolExecutor.AbortPolicy()
);
```

### 3. 虚拟线程 vs 平台线程

| 场景 | 推荐方案 | 原因 |
|-----|---------|------|
| IO 密集型 + 高并发 | 虚拟线程 | 创建成本低，阻塞不占用平台线程 |
| CPU 密集型 | 平台线程池 | 虚拟线程仍运行在平台线程上 |
| 混合场景 | 根据任务特点选择 | IO 部分用虚拟线程，CPU 部分用平台线程 |

---

## 监控与排查

### 1. 日志格式说明

```
[userId:1001] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123]  [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  INFO  c.w.w.controller.AppController - 处理请求成功
```

- `userId`: 用户 ID，未登录显示 `NULL`
- `traceId`: 链路追踪 ID，用于关联同一请求的所有日志
- `IP`: 客户端真实 IP
- `lang`: 客户端语言
- `zone`: 客户端时区

### 2. 根据 traceId 追踪请求

```bash
# 查询某个 traceId 的所有日志
grep "traceId:abc123" logs/application.log

# 查询某个用户的所有日志
grep "userId:1001" logs/application.log
```  

### 3. 线程池监控

```java
@RestController
@RequestMapping("/monitor")
public class MonitorController {
    
    @GetMapping("/thread-pools")
    public Map<String, String> getThreadPoolStats(
            @Autowired @Qualifier("businessThreadPool") ThreadPoolTaskExecutor businessPool,
            @Autowired @Qualifier("ioThreadPool") ThreadPoolTaskExecutor ioPool) {
        
        Map<String, String> stats = new HashMap<>();
        
        // 业务线程池统计
        ThreadPoolExecutor executor = businessPool.getThreadPoolExecutor();
        stats.put("business.activeCount", String.valueOf(executor.getActiveCount()));
        stats.put("business.poolSize", String.valueOf(executor.getPoolSize()));
        stats.put("business.queueSize", String.valueOf(executor.getQueue().size()));
        stats.put("business.completedTaskCount", String.valueOf(executor.getCompletedTaskCount()));
        
        // IO 线程池统计
        executor = ioPool.getThreadPoolExecutor();
        stats.put("io.activeCount", String.valueOf(executor.getActiveCount()));
        stats.put("io.poolSize", String.valueOf(executor.getPoolSize()));
        stats.put("io.queueSize", String.valueOf(executor.getQueue().size()));
        
        return stats;
    }
    
    @GetMapping("/virtual-thread-pools")
    public String getVirtualThreadPoolStats() {
        return VirtualThreadPoolConfig.getMonitorInfo();
    }
}
```

### 4. 常见问题排查

#### 问题 1：日志中 traceId 显示 NULL

**原因：** 请求未经过 `MdcFilter` 过滤器。

**解决：** 确保 `MdcFilter` 已注册，且优先级最高（`@Order(Ordered.HIGHEST_PRECEDENCE)`）。

#### 问题 2：异步线程中 MDC 信息丢失

**原因：** 使用了未配置 `MdcTaskDecorator` 的线程池。

**解决：** 使用项目提供的预配置线程池，或手动设置 `TaskDecorator`：

```java
executor.setTaskDecorator(new MdcTaskDecorator());
```

#### 问题 3：响应式编程中 MDC 信息丢失

**原因：** 未调用 `contextWrite(ReactorMdcUtils.captureMdc())`。

**解决：** 在 Flux/Mono 链路末尾添加：

```java
.contextWrite(ReactorMdcUtils.captureMdc())
```

#### 问题 4：虚拟线程中 MDC 信息丢失

**原因：** 使用了原生 `Thread.ofVirtual()` 创建虚拟线程。

**解决：** 使用 `VirtualThreadExecutor` 封装类。

---

## 总结

本 MDC 管理方案的核心设计思想：

1. **自动化**：在请求入口自动装载 MDC，无需手动干预
2. **透明化**：异步线程池、虚拟线程池自动传递 MDC
3. **可控化**：响应式编程中由调用者选择性传递，平衡性能与功能
4. **易用性**：API 简洁，调用方无需过多关心细节
5. **可监控**：提供完善的监控和统计功能

通过合理使用本方案，可以在各种并发场景下保持日志的一致性和可追踪性，极大提升问题排查效率。

