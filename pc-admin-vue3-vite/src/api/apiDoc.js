import axios from 'axios'

// OpenAPI 文档端点（/v3/api-docs）位于服务根路径，不带 /api 前缀，
// 因此不能用统一的 request()（其 baseURL 经过 vite 代理转发 /api）。
// 这里独立创建一个 baseURL='/' 的 axios 实例，仅用于拉取 OpenAPI JSON，
// 并手工注入 JWT（springdoc 在 prod 关闭、dev 开放；dev 下本地可能无需鉴权，
// 带上 token 无害）。错误由调用方 try/catch 处理（不使用全局响应拆包拦截器）。
const docAxios = axios.create({ baseURL: '/', timeout: 20000 })

docAxios.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('pc_admin_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

/**
 * 获取 OpenAPI 文档（springdoc 默认 JSON 端点 /v3/api-docs）。
 * @returns {Promise<object>} OpenAPI 文档对象（paths / components 等）
 */
export function getApiDocs() {
  return docAxios.get('/v3/api-docs').then((r) => r.data)
}
