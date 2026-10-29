import { rawAxios } from './http'

/**
 * 健康探针模块（k8s 风格根路径端点）。
 *
 * 后端 scaffold-monitor 的 ProbeController 提供两个端点：
 *   - GET /livez  ：存活探针，仅依赖 JVM，永不直接依赖外部服务；
 *   - GET /readyz ：就绪探针，检查数据库等必需依赖，不可用时返回 503。
 * 两者均在 SecurityConfig 中 permitAll（探针不应要求鉴权），且无 /api 前缀。
 *
 * 响应体为统一的 ProbeResult {status:'UP'|'DOWN', message, details}，**并非** 后端通用的
 * Result<T> 包络。因此不能使用 request()（响应拦截器会按 Result<T> 拆包并把非 "0" 码当作错误弹窗）。
 * 同时 503 是合法的 DOWN 业务态（如数据库不可用），不应被 http.js 的 handleHttpError 误判为网络错误，
 * 故此处用 rawAxios + validateStatus: () => true 透传 HTTP 状态，由调用方依据 status 字段判定。
 *
 * 注意：vite dev 代理需将 /livez、/readyz 转发到后端（见 vite.config.js）；
 * 生产环境由同源后端直接处理根路径探针。
 *
 * @returns {Promise<{ok:boolean, httpStatus:number, status?:string, message?:string, details?:Object, error?:string}>}
 *   ok=false 时携带 error（i18n key：'health.unreachable' | 'health.format'）。
 */
function fetchProbe(url) {
  return rawAxios
    .request({ method: 'GET', url, validateStatus: () => true })
    .then((resp) => {
      const data = resp.data
      if (!data || typeof data.status !== 'string') {
        // 响应体非预期结构：通常是代理未生效，浏览器拿到了前端 SPA 兜底 HTML（200）。
        return { ok: false, httpStatus: resp.status, error: 'health.format' }
      }
      return {
        ok: true,
        httpStatus: resp.status,
        status: data.status,
        message: data.message,
        details: data.details || {}
      }
    })
    .catch((err) => ({ ok: false, httpStatus: 0, error: 'health.unreachable', raw: err.message }))
}

// GET /livez
export const getLiveness = () => fetchProbe('/livez')

// GET /readyz
export const getReadiness = () => fetchProbe('/readyz')
