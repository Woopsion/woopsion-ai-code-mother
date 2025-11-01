# MdcFilter 配置说明

## 概述

`MdcFilter` 是一个智能的 MDC（Mapped Diagnostic Context）过滤器，支持基于 URL 路径的白名单机制，可以灵活控制哪些请求需要装载 MDC 信息。

---

## 白名单机制

### 1. 三种过滤级别

| 级别 | 说明 | MDC 装载内容 | 典型场景 |
|-----|------|------------|---------|
| **完全跳过** | 不装载任何 MDC | 无 | 静态资源、API 文档 |
| **基础 MDC** | 装载基础信息 | traceId、IP、lang、zone | 登录、注册、登出 |
| **完整 MDC** | 装载全部信息 | traceId、userId、IP、lang、zone | 业务接口 |

### 2. 完全跳过 MDC 的路径（`SKIP_MDC_PATHS`）

这些路径**完全不装载 MDC**，直接放行，以提升性能。

**默认配置：**
```java
private static final List<String> SKIP_MDC_PATHS = Arrays.asList(
    // 静态资源
    "/static/**",           // 静态资源目录
    "/public/**",           // 公共资源目录
    "/resources/**",        // 资源文件目录
    "/webjars/**",          // WebJars 资源
    "/*.ico",               // 图标文件
    "/*.html",              // HTML 文件（根目录）
    "/*.js",                // JavaScript 文件（根目录）
    "/*.css",               // CSS 文件（根目录）
    "/*.png",               // PNG 图片（根目录）
    "/*.jpg",               // JPG 图片（根目录）
    "/*.jpeg",              // JPEG 图片（根目录）
    "/*.gif",               // GIF 图片（根目录）
    "/*.svg",               // SVG 图片（根目录）
    "/*.woff",              // 字体文件（根目录）
    "/*.woff2",             // 字体文件（根目录）
    "/*.ttf",               // 字体文件（根目录）
    "/*.eot",               // 字体文件（根目录）
    
    // API 文档相关（knife4j、swagger）
    "/api/doc.html/**",     // Knife4j API 文档（及子路径）
    "/doc.html/**",         // API 文档（及子路径）
    "/swagger-ui.html/**",  // Swagger UI（及子路径）
    "/swagger-ui/**",       // Swagger UI 资源
    "/swagger-resources/**",// Swagger 资源
    "/v2/api-docs/**",      // Swagger v2 API 文档
    "/v3/api-docs/**",      // Swagger v3 API 文档
    "/favicon.ico"          // 网站图标
);
```

**路径匹配规则：**
- `/**` - 匹配该路径下的所有子路径
- `/*.xxx` - 匹配根目录下指定扩展名的文件

### 3. 基础 MDC 路径（`BASIC_MDC_PATHS`）

这些路径**装载基础 MDC**（traceId、IP、lang、zone），但**不装载 userId**。

**默认配置：**
```java
private static final List<String> BASIC_MDC_PATHS = Arrays.asList(
    "/user/login",      // 用户登录
    "/user/register",   // 用户注册
    "/user/logout"      // 用户登出
);
```

**适用场景：**
- 用户未登录时访问的接口
- 登录、注册等接口（此时还没有 userId）
- 公开 API 接口

### 4. 完整 MDC 路径（默认）

除了上述两类路径外的**所有路径**，都会装载**完整 MDC**。

**MDC 内容：**
- `traceId` - 链路追踪 ID
- `userId` - 用户 ID（如果已登录）
- `clientIP` - 客户端 IP
- `lang` - 客户端语言
- `zone` - 客户端时区

---

## 自定义配置

### 方式 1：直接修改源码

修改 `MdcFilter.java` 中的常量列表：

```java
// 添加新的跳过路径
private static final List<String> SKIP_MDC_PATHS = Arrays.asList(
    // 原有配置...
    
    // 自定义：健康检查接口
    "/actuator/**",
    "/health/**",
    
    // 自定义：下载文件
    "/download/**",
    "/export/**"
);

// 添加新的基础 MDC 路径
private static final List<String> BASIC_MDC_PATHS = Arrays.asList(
    // 原有配置...
    
    // 自定义：验证码接口
    "/captcha/**",
    
    // 自定义：第三方回调
    "/callback/**"
);
```

### 方式 2：通过配置文件（推荐）

*待实现：未来可以将白名单配置外部化到 `application.yml`*

---

## 日志输出示例

### 1. 完全跳过 MDC 的路径

```bash
# 访问静态资源：/static/css/style.css
# 日志输出：无（直接跳过，不打印任何 MDC 日志）
```

### 2. 基础 MDC 路径

```bash
# 访问登录接口：POST /user/login
[userId:NULL] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123]  [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  INFO  c.w.w.controller.UserController - 用户登录请求
```

**注意：** `userId` 显示为 `NULL`，因为用户还未登录。

### 3. 完整 MDC 路径

```bash
# 访问业务接口：GET /app/get/vo?id=1001
[userId:1001] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123]  [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  INFO  c.w.w.controller.AppController - 查询应用详情
```

**注意：** `userId` 显示为实际的用户 ID。

---

## 路径匹配规则

### AntPathMatcher 匹配规则

| 模式 | 说明 | 示例 |
|-----|------|------|
| `?` | 匹配单个字符 | `/user/?.html` 匹配 `/user/a.html` |
| `*` | 匹配任意数量的字符（不包括 `/`） | `/user/*.html` 匹配 `/user/test.html` |
| `**` | 匹配任意数量的目录和字符 | `/user/**` 匹配 `/user/a/b/c` |
| `{name}` | 匹配路径变量 | `/user/{id}` 匹配 `/user/123` |

### 常见匹配示例

```java
// 匹配所有图片
"/**/*.jpg", "/**/*.png", "/**/*.gif"

// 匹配指定目录下的所有文件
"/images/**"

// 匹配多级目录
"/api/**/public/**"

// 匹配根目录的特定文件
"/*.html"
```

---

## 性能优化

### 1. 完全跳过 vs 基础 MDC

| 方式 | 性能 | 说明 |
|-----|------|------|
| 完全跳过 | ⚡⚡⚡ 最快 | 直接放行，无任何 MDC 操作 |
| 基础 MDC | ⚡⚡ 较快 | 只装载基础信息，不查询用户 |
| 完整 MDC | ⚡ 正常 | 需要查询用户信息 |

### 2. 建议

- ✅ **静态资源**：使用"完全跳过"
- ✅ **API 文档**：使用"完全跳过"
- ✅ **登录注册**：使用"基础 MDC"
- ✅ **业务接口**：使用"完整 MDC"

---

## 常见问题

### Q1: 如何放行整个目录？

使用 `/**` 通配符：

```java
"/images/**",        // 放行 /images 目录及其所有子目录
"/static/**",        // 放行 /static 目录及其所有子目录
```

### Q2: 如何放行特定扩展名的文件？

使用 `*.xxx` 通配符：

```java
"/**/*.pdf",         // 放行所有 PDF 文件
"/**/*.zip",         // 放行所有 ZIP 文件
```

### Q3: 如何区分根目录和子目录的文件？

```java
"/*.js",             // 仅放行根目录的 JS 文件（如 /app.js）
"/**/*.js",          // 放行所有目录的 JS 文件（如 /static/js/app.js）
```

### Q4: Knife4j 访问不了怎么办？

确保以下路径在 `SKIP_MDC_PATHS` 中：

```java
"/api/doc.html/**",
"/doc.html/**",
"/swagger-ui/**",
"/swagger-resources/**",
"/v2/api-docs/**",
"/v3/api-docs/**",
"/webjars/**"
```

如果还不行，检查您的 Knife4j 配置路径是否与白名单匹配。

### Q5: 为什么登录后日志还显示 userId:NULL？

可能原因：
1. 该路径在 `BASIC_MDC_PATHS` 中（故意不装载 userId）
2. `userService.getLoginUser()` 方法返回了 `null`
3. Token 无效或已过期

检查方法：
```bash
# 查看日志，确认是否为基础 MDC 路径
grep "基础 MDC 装载完成" logs/application.log

# 查看日志，确认用户信息是否获取成功
grep "用户 ID 装载完成" logs/application.log
```

---

## 测试建议

### 1. 测试完全跳过的路径

```bash
# 访问静态资源
curl http://localhost:8080/static/css/style.css

# 访问 Knife4j 文档
curl http://localhost:8080/api/doc.html

# 预期结果：请求成功，但日志中不显示 MDC 信息
```

### 2. 测试基础 MDC 路径

```bash
# 访问登录接口
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -H "lang: zh-CN" \
  -H "zone: Asia/Shanghai" \
  -d '{"userAccount":"test","userPassword":"123456"}'

# 预期结果：日志显示 traceId、IP、lang、zone，但 userId 显示 NULL
```

### 3. 测试完整 MDC 路径

```bash
# 访问业务接口（需要登录）
curl http://localhost:8080/app/my/list/page/vo \
  -H "Authorization: Bearer <token>" \
  -H "lang: zh-CN" \
  -H "zone: Asia/Shanghai"

# 预期结果：日志显示完整的 MDC 信息，包括 userId
```

---

## 维护建议

### 1. 定期审查白名单

建议每个迭代审查一次白名单配置，确保：
- ✅ 静态资源路径正确
- ✅ 新增的公开接口已添加到白名单
- ✅ 不必要的路径及时移除

### 2. 日志监控

定期检查日志，确认 MDC 装载是否正常：

```bash
# 查看哪些路径被跳过
grep "跳过 MDC 装载" logs/application.log

# 查看基础 MDC 路径
grep "基础 MDC 装载完成" logs/application.log

# 查看用户 ID 装载情况
grep "用户 ID 装载完成" logs/application.log
```

### 3. 性能监控

监控过滤器的性能影响：
- 统计"完全跳过"路径的访问量
- 统计"基础 MDC"路径的访问量
- 统计"完整 MDC"路径的访问量

根据统计结果优化白名单配置。

---

## 总结

`MdcFilter` 的白名单机制提供了**三级过滤策略**：

1. **完全跳过**：静态资源、API 文档 → 最快，无 MDC
2. **基础 MDC**：登录注册接口 → 较快，有基础追踪
3. **完整 MDC**：业务接口 → 正常，完整追踪

通过合理配置白名单，可以在保证日志追踪能力的同时，最大化系统性能。


