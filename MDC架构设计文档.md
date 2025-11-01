# MDC（Mapped Diagnostic Context）架构设计文档

## 一、设计目标

为生产环境提供一套**专业、高效、易用**的 MDC 管理方案，确保在多种并发场景下都能正确传递和打印日志上下文信息。

### 核心要求

1. ✅ **自动装载**：在请求入口自动装载 MDC（traceId、userId、IP、lang、zone 等）
2. ✅ **无缝传递**：支持异步线程池、响应式编程、虚拟线程中的 MDC 传递
3. ✅ **性能优化**：调用者可选择性传递 MDC，避免高频操作中的性能损耗
4. ✅ **易用性**：API 简洁，调用方无需过多关心底层实现
5. ✅ **可监控**：提供线程池和虚拟线程池的监控功能

---

## 二、架构设计

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP 请求入口                             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      MdcFilter (过滤器)                          │
│  - 生成/获取 traceId                                              │
│  - 获取客户端 IP                                                  │
│  - 从请求头提取 lang、zone                                        │
│  - 装载到 MDC                                                     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
        ┌─────────────────────┴─────────────────────┐
        │                                           │
┌───────▼────────┐                    ┌─────────────▼──────────┐
│  同步代码路径    │                    │    异步/并发路径        │
│  - Controller   │                    │                        │
│  - Service      │                    │                        │
│  - MDC 自动可用  │                    │                        │
└────────────────┘                    └────────┬───────────────┘
                                               │
                    ┌──────────────────────────┼──────────────────────────┐
                    │                          │                          │
            ┌───────▼────────┐      ┌──────────▼──────────┐    ┌─────────▼─────────┐
            │  异步线程池     │      │   响应式编程         │    │    虚拟线程        │
            │  MdcTaskDecorator│    │  ReactorMdcUtils    │    │ VirtualThreadExecutor│
            │  - 任务提交时     │      │  - captureMdc()    │    │  - submit()       │
            │    复制 MDC      │      │  - withMdc()       │    │  - supplyAsync()  │
            │  - 执行时恢复    │      │  - contextWrite()  │    │  - MDC 自动传递    │
            └────────────────┘      └────────────────────┘    └───────────────────┘
```

### 2.2 核心组件

#### 2.2.1 MDC 常量类 (`MdcConstant`)

定义 MDC 键名常量，统一管理。

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

#### 2.2.2 MDC 工具类 (`MdcUtils`)

提供 MDC 的基础操作：
- `getCopyOfContextMap()` - 获取 MDC 快照
- `setContextMap()` - 设置 MDC
- `clear()` - 清理 MDC
- `generateAndSetTraceId()` - 生成追踪 ID
- `wrap(Runnable)` / `wrap(Callable)` - 包装任务

#### 2.2.3 MDC 过滤器 (`MdcFilter`)

**设计决策：使用过滤器而非拦截器**

| 对比项 | 过滤器 (Filter) | 拦截器 (Interceptor) |
|-------|----------------|---------------------|
| 执行层级 | Servlet 容器层面 | Spring MVC 层面 |
| 执行时机 | 更早（在 DispatcherServlet 之前） | 较晚（在 Controller 之前） |
| 覆盖范围 | 所有请求（包括静态资源） | 仅 Spring MVC 处理的请求 |
| 优先级 | `@Order(Ordered.HIGHEST_PRECEDENCE)` | 配置顺序 |

**选择过滤器的原因：**
1. ✅ 执行更早，能覆盖所有请求
2. ✅ 确保整个请求链路都有 MDC 信息
3. ✅ 在请求结束时统一清理 MDC，避免内存泄漏

**白名单机制（三级过滤）：**

| 级别 | MDC 装载内容 | 典型场景 | 性能 |
|-----|------------|---------|------|
| **完全跳过** | 无 | 静态资源、API 文档 | ⚡⚡⚡ |
| **基础 MDC** | traceId、IP、lang、zone | 登录、注册 | ⚡⚡ |
| **完整 MDC** | traceId、userId、IP、lang、zone | 业务接口 | ⚡ |

**关键实现：**
```java
@Override
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
    try {
        if (request instanceof HttpServletRequest httpRequest) {
            String requestURI = httpRequest.getRequestURI();
            
            // 1. 判断是否跳过 MDC（静态资源、API 文档）
            if (shouldSkipMdc(requestURI)) {
                chain.doFilter(request, response);
                return;
            }
            
            // 2. 装载基础 MDC（traceId、IP、lang、zone）
            setupBasicMdc(httpRequest);
            
            // 3. 判断是否装载 userId（业务接口）
            if (!isBasicMdcPath(requestURI)) {
                setupUserId(httpRequest);
            }
        }
        
        chain.doFilter(request, response);
    } finally {
        // 4. 清理 MDC（避免内存泄漏）
        MdcUtils.clear();
    }
}
```

**默认白名单配置：**
```java
// 完全跳过（静态资源、API 文档）
SKIP_MDC_PATHS = [
    "/static/**", "/api/doc.html/**", "/swagger-ui/**", 
    "/*.ico", "/*.js", "/*.css", "/*.png", ...
]

// 基础 MDC（登录注册）
BASIC_MDC_PATHS = [
    "/user/login", "/user/register", "/user/logout"
]
```

#### 2.2.4 异步线程池配置 (`AsyncThreadPoolConfig`)

提供 5 种预配置的线程池，每种针对不同场景优化：

| 线程池 | 场景 | 核心线程数 | 最大线程数 | 队列容量 | 拒绝策略 |
|-------|------|-----------|-----------|---------|---------|
| businessThreadPool | 一般业务 | 10 | 30 | 200 | CallerRunsPolicy |
| ioThreadPool | IO 密集型 | 50 | 100 | 500 | CallerRunsPolicy |
| cpuThreadPool | CPU 密集型 | CPU核心数+1 | CPU核心数×2 | 100 | AbortPolicy |
| fastThreadPool | 快速任务 | 5 | 20 | 50 | DiscardOldestPolicy |
| monitorThreadPool | 监控统计 | 2 | 5 | 1000 | DiscardPolicy |

**MDC 传递原理：**
```java
executor.setTaskDecorator(new MdcTaskDecorator());
```

#### 2.2.5 MDC 任务装饰器 (`MdcTaskDecorator`)

**核心原理：**
1. 任务提交时（主线程）：复制当前线程的 MDC
2. 任务执行时（子线程）：将 MDC 设置到子线程
3. 任务完成后：清理子线程的 MDC

```java
@Override
public Runnable decorate(Runnable runnable) {
    Map<String, String> contextMap = MdcUtils.getCopyOfContextMap(); // 提交时复制
    return () -> {
        Map<String, String> previous = MdcUtils.getCopyOfContextMap();
        try {
            MdcUtils.setContextMap(contextMap); // 执行时设置
            runnable.run();
        } finally {
            MDC.clear(); // 执行后清理
            MdcUtils.setContextMap(previous);
        }
    };
}
```

#### 2.2.6 Reactor MDC 工具类 (`ReactorMdcUtils`)

**设计挑战：**
- Reactor 响应式流可能在不同线程中执行
- 传统的 ThreadLocal（MDC）无法自动传递
- 高频操作（如 `map`）不能每次都复制 MDC（性能问题）

**设计方案：**

1. **使用 Reactor Context 传递 MDC**
   ```java
   Flux.just("data")
       .contextWrite(ReactorMdcUtils.captureMdc()) // 捕获 MDC 到 Context
   ```

2. **选择性传递 MDC**
   ```java
   // ❌ 高频操作：不传递 MDC
   .map(String::toUpperCase)
   
   // ✅ 需要日志：传递 MDC
   .doOnNext(ReactorMdcUtils.withMdc(value -> {
       log.info("数据: {}", value); // 包含 MDC 信息
   }))
   ```

3. **提供多种包装方法**
   - `withMdc(Consumer<T>)` - 包装 Consumer
   - `withMdc(Runnable)` - 包装 Runnable
   - `withMdc(Function<T, R>)` - 包装 Function
   - `captureMdc()` - 捕获 MDC 到 Context
   - `deferFluxWithMdc()` / `deferMonoWithMdc()` - 延迟执行

#### 2.2.7 虚拟线程执行器 (`VirtualThreadExecutor`)

**虚拟线程优势：**
- ✅ 创建成本极低（可创建数百万个）
- ✅ 阻塞操作不占用平台线程
- ✅ 非常适合 IO 密集型、高并发场景

**MDC 传递实现：**
```java
private Runnable wrapWithMdc(Runnable task) {
    Map<String, String> contextMap = MdcUtils.getCopyOfContextMap();
    return () -> {
        Map<String, String> previous = MdcUtils.getCopyOfContextMap();
        try {
            MdcUtils.setContextMap(contextMap);
            task.run();
        } finally {
            MDC.clear();
            MdcUtils.setContextMap(previous);
        }
    };
}
```

#### 2.2.8 虚拟线程池配置 (`VirtualThreadPoolConfig`)

提供 3 种预配置的虚拟线程池：
- `businessVirtualThreadPool` - 一般业务
- `ioVirtualThreadPool` - IO 密集型
- `asyncVirtualThreadPool` - 异步消息处理

**监控功能：**
- 自动注册所有虚拟线程池
- 定时打印统计信息（每 5 分钟）
- 提供手动查询 API

---

## 三、性能优化策略

### 3.1 响应式编程性能优化

**问题场景：** SSE 流式输出大量数据

```java
// 场景：AI 代码生成，每秒返回数百个 chunk
Flux<String> generateCode(String prompt) {
    return aiService.stream(prompt)
        // ❌ 错误：每个 chunk 都复制 MDC（性能差）
        .map(ReactorMdcUtils.withMdc(chunk -> wrapToJson(chunk)))
        
        // ✅ 正确：高频 map 不传递 MDC
        .map(chunk -> wrapToJson(chunk))
        // 只在需要日志的地方传递 MDC
        .doOnError(ReactorMdcUtils.withMdc(error -> {
            log.error("生成失败", error);
        }))
        .contextWrite(ReactorMdcUtils.captureMdc());
}
```

**性能对比：**

| 方案 | 每秒操作数 | MDC 复制次数 | 性能影响 |
|-----|-----------|-------------|---------|
| 每个 map 都传递 MDC | 1000 | 1000 | 严重 ⚠️ |
| 仅在日志处传递 MDC | 1000 | 2-3 | 轻微 ✅ |

### 3.2 线程池配置优化

**IO 密集型优化：**
```java
// IO 密集型：线程数可以更多
ThreadPoolTaskExecutor ioPool = createCustomThreadPool(
    "io-pool-",
    50,   // 核心线程数
    100,  // 最大线程数
    500,  // 队列容量
    120,  // 存活时间（秒）
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

**CPU 密集型优化：**
```java
// CPU 密集型：线程数不宜过多
int processors = Runtime.getRuntime().availableProcessors();
ThreadPoolTaskExecutor cpuPool = createCustomThreadPool(
    "cpu-pool-",
    processors + 1,
    processors * 2,
    100,
    30,
    new ThreadPoolExecutor.AbortPolicy()
);
```

### 3.3 虚拟线程 vs 平台线程

| 场景 | 推荐方案 | 原因 |
|-----|---------|------|
| IO 密集型 + 高并发 | 虚拟线程 | 创建成本低，阻塞不占用平台线程 |
| CPU 密集型 | 平台线程池 | 虚拟线程仍运行在平台线程上 |
| 混合场景 | 分别使用 | IO 用虚拟线程，CPU 用平台线程 |

---

## 四、使用示例

### 4.1 普通同步代码

```java
@RestController
public class UserController {
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        // MDC 已自动装载，直接打印日志
        log.info("查询用户: id={}", id);
        return userService.getUser(id);
    }
}
```

### 4.2 异步线程池

```java
@Service
public class OrderService {
    
    @Autowired
    @Qualifier("businessThreadPool")
    private ThreadPoolTaskExecutor businessThreadPool;
    
    public void processOrder(Order order) {
        businessThreadPool.submit(() -> {
            log.info("处理订单: orderId={}", order.getId());
            // MDC 自动传递到子线程
        });
    }
}
```

### 4.3 响应式编程

```java
public Flux<String> processData() {
    return Flux.just("a", "b", "c")
        .map(String::toUpperCase)  // 高频操作，不传递 MDC
        .doOnNext(ReactorMdcUtils.withMdc(value -> {
            log.info("处理数据: {}", value);  // 需要日志，传递 MDC
        }))
        .contextWrite(ReactorMdcUtils.captureMdc());
}
```

### 4.4 虚拟线程

```java
@Service
public class FileService {
    
    @Autowired
    @Qualifier("ioVirtualThreadPool")
    private VirtualThreadExecutor ioVirtualThreadPool;
    
    public CompletableFuture<String> processFile(String file) {
        return ioVirtualThreadPool.supplyAsync(() -> {
            log.info("处理文件: {}", file);
            // MDC 自动传递到虚拟线程
            return "success";
        });
    }
}
```

---

## 五、监控与排查

### 5.1 日志格式

```
[userId:1001] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123]  [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  INFO  c.w.w.controller.AppController - 处理请求
```

### 5.2 链路追踪

```bash
# 根据 traceId 追踪完整请求链路
grep "traceId:abc123" logs/application.log

# 查看某个用户的所有操作
grep "userId:1001" logs/application.log
```

### 5.3 线程池监控

```java
// 获取线程池统计
ThreadPoolExecutor executor = businessThreadPool.getThreadPoolExecutor();
int activeCount = executor.getActiveCount();
int poolSize = executor.getPoolSize();
int queueSize = executor.getQueue().size();
long completedTaskCount = executor.getCompletedTaskCount();
```

### 5.4 虚拟线程池监控

```java
// 获取虚拟线程池统计
String stats = ioVirtualThreadPool.getStatistics();
int activeTasks = ioVirtualThreadPool.getActiveTaskCount();
int totalTasks = ioVirtualThreadPool.getTotalTaskCount();

// 获取所有虚拟线程池
Map<String, VirtualThreadExecutor> executors = VirtualThreadPoolConfig.getAllExecutors();
String monitorInfo = VirtualThreadPoolConfig.getMonitorInfo();
```

---

## 六、常见问题与解决方案

### 6.1 MDC 信息丢失

| 问题 | 原因 | 解决方案 |
|-----|------|---------|
| traceId 显示 NULL | 请求未经过 MdcFilter | 确保 MdcFilter 优先级最高 |
| 异步线程中丢失 | 未使用 MdcTaskDecorator | 使用预配置线程池或手动设置 TaskDecorator |
| 响应式流中丢失 | 未调用 contextWrite | 添加 `.contextWrite(ReactorMdcUtils.captureMdc())` |
| 虚拟线程中丢失 | 使用原生 Thread.ofVirtual() | 使用 VirtualThreadExecutor 封装类 |

### 6.2 性能问题

| 问题 | 原因 | 解决方案 |
|-----|------|---------|
| SSE 流性能差 | 高频 map 中传递 MDC | 只在需要日志的地方传递 MDC |
| 线程池任务积压 | 线程池配置不合理 | 根据任务类型选择合适的线程池 |
| 内存泄漏 | MDC 未清理 | 确保在 finally 块中清理 MDC |

---

## 七、总结

### 7.1 设计亮点

1. ✅ **过滤器装载**：在请求入口统一装载 MDC，覆盖所有请求
2. ✅ **自动传递**：异步线程池、虚拟线程池自动传递 MDC
3. ✅ **性能优化**：响应式编程中可选择性传递，避免高频操作损耗
4. ✅ **易用性**：API 简洁，调用方无需关心底层实现
5. ✅ **可监控**：提供完善的监控和统计功能

### 7.2 适用场景

| 场景 | 方案 | 优势 |
|-----|------|------|
| Web 应用日志追踪 | MdcFilter + 日志格式 | 统一的日志格式，易于追踪 |
| 异步任务处理 | 预配置线程池 | MDC 自动传递，无需手动处理 |
| 响应式编程 | ReactorMdcUtils | 灵活的 MDC 传递，性能优化 |
| 高并发 IO 场景 | 虚拟线程池 | 轻量级，支持百万级并发 |

### 7.3 生产环境建议

1. ✅ 使用预配置的线程池，避免手动创建
2. ✅ 响应式编程中只在需要日志的地方传递 MDC
3. ✅ 定期监控线程池使用情况，及时调整参数
4. ✅ 对于 IO 密集型高并发场景，优先使用虚拟线程
5. ✅ 确保异常处理中也能记录 MDC 信息

---

## 八、版本信息

- **创建时间**: 2025-11-01
- **适用版本**: Spring Boot 3.x + Java 21+
- **核心依赖**: Reactor、Logback、Spring MVC
- **作者**: woopsion

---

**完整代码位置：**
- 常量类：`src/main/java/com/woopsion/woopsionaicodemother/constant/MdcConstant.java`
- 工具类：`src/main/java/com/woopsion/woopsionaicodemother/utils/MdcUtils.java`
- 过滤器：`src/main/java/com/woopsion/woopsionaicodemother/filter/MdcFilter.java`
- 线程池配置：`src/main/java/com/woopsion/woopsionaicodemother/config/AsyncThreadPoolConfig.java`
- Reactor 工具：`src/main/java/com/woopsion/woopsionaicodemother/utils/ReactorMdcUtils.java`
- 虚拟线程：`src/main/java/com/woopsion/woopsionaicodemother/utils/VirtualThreadExecutor.java`
- 使用示例：`src/main/java/com/woopsion/woopsionaicodemother/example/MdcUsageExamples.java`

