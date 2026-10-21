import { request } from './http'

/**
 * 前后端联通探针模块。
 *
 * 仅一个端点：GET /api/connectivity/ping，供前端在启动时/心跳中探测后端存活、
 * 时钟与多语言协商结果。后端返回统一包络 Result<ConnectivityProbe>，
 * 经 src/api/http.js 响应拦截器拆包后，此处直接拿到 data。
 *
 * @returns {Promise<{serverTime:string, receivedLocale:string, nonce:string, framework:string}>}
 *   serverTime  — 后端 UTC 时钟（ISO-8601），用于粗校验前后端时钟漂移；
 *   receivedLocale — 后端实际解析到的语言标签（验证多语言协商）；
 *   nonce      — 每次随机串，用于判别是否为同一响应（防缓存误判联通）；
 *   framework  — 后端框架标识，固定为 "java-admin-framework"。
 */
export function ping() {
  return request({ method: 'GET', url: '/api/connectivity/ping' })
}
