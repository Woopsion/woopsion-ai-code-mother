/**
 * MDC 过滤器配置包
 * 
 * <p>本包包含 MDC（Mapped Diagnostic Context）过滤器的实现和配置。</p>
 * 
 * <h2>核心功能</h2>
 * <ul>
 *   <li>在请求入口自动装载 MDC 信息（traceId、userId、IP、lang、zone）</li>
 *   <li>支持基于 URL 的白名单机制，灵活控制 MDC 装载</li>
 *   <li>提供三级过滤策略：完全跳过、基础 MDC、完整 MDC</li>
 * </ul>
 * 
 * <h2>白名单机制</h2>
 * <table border="1">
 *   <tr>
 *     <th>级别</th>
 *     <th>MDC 装载内容</th>
 *     <th>典型场景</th>
 *   </tr>
 *   <tr>
 *     <td>完全跳过</td>
 *     <td>无</td>
 *     <td>静态资源、API 文档</td>
 *   </tr>
 *   <tr>
 *     <td>基础 MDC</td>
 *     <td>traceId、IP、lang、zone</td>
 *     <td>登录、注册</td>
 *   </tr>
 *   <tr>
 *     <td>完整 MDC</td>
 *     <td>traceId、userId、IP、lang、zone</td>
 *     <td>业务接口</td>
 *   </tr>
 * </table>
 * 
 * <h2>使用示例</h2>
 * <pre>
 * // 自动生效，无需配置
 * // MdcFilter 会自动注册为 Spring Bean，并按优先级执行
 * 
 * // 日志输出（完整 MDC）：
 * [userId:1001] 2025-11-01 10:30:45.123 [http-nio-8080-exec-1] [traceId:abc123]  
 * [IP:192.168.1.100] [lang:zh-CN] [zone:Asia/Shanghai]  
 * INFO  c.w.w.controller.AppController - 处理请求
 * </pre>
 * 
 * <h2>配置说明</h2>
 * <p>详细配置说明请参考：{@link MdcFilter}</p>
 * <p>或查看文档：{@code MdcFilterConfiguration.md}</p>
 * 
 * @see MdcFilter
 * @author <a href="https://github.com/Woopsion">woopsion</a>
 */
package com.woopsion.woopsionaicodemother.config.filter;


