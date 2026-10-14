import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_BASE } from './config'
import { clearAuth } from '../store/auth'

const realAxios = axios.create({ baseURL: API_BASE, timeout: 15000 })

// 稳定的设备标识：每个浏览器首次访问时生成并持久化，用于后端「同账号多端登录限制」统计在线设备数。
// 该标识随每个请求经 X-Device-Id 头上报，后端据此区分不同登录设备。
function getDeviceId() {
  let id = localStorage.getItem('pc_admin_device_id')
  if (id) return id
  try {
    id = (typeof crypto !== 'undefined' && crypto.randomUUID)
      ? crypto.randomUUID()
      : 'dev-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
  } catch (e) {
    id = 'dev-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
  }
  localStorage.setItem('pc_admin_device_id', id)
  return id
}

// 请求拦截：注入 JWT 与设备标识
realAxios.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('pc_admin_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  if (!cfg.headers['X-Device-Id']) cfg.headers['X-Device-Id'] = getDeviceId()
  return cfg
})

// 响应拦截：统一拆包 Result<T>{code,message,data}，code!=='0' 视为业务错误
realAxios.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code === '0') return body.data
    const msg = body?.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(Object.assign(new Error(msg), { code: body?.code }))
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      clearAuth()
      import('../router').then((m) => m.default.replace('/login'))
    }
    const msg = err.response?.data?.message || err.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

/**
 * 统一请求入口：页面与组件获取数据的唯一通道（后端为唯一数据源，前端无任何 mock 分支）。
 *
 * 响应拦截器已拆包 Result<T>{code,message,data}，
 * 因此 API 模块与组件拿到的直接是 data，无需再判 code。
 */
export function request(config) {
  const { method = 'GET', url, params, data, headers } = config
  return realAxios.request({ method, url, params, data, headers })
}

// 原始请求通道：不经过「拆包」响应拦截，用于文件下载（blob）等需要原始响应的场景。
// responseType 等调用方显式声明的 axios 配置必须透传——早前白名单式解构只取了
// method/url/params/data/headers，导致调用方传的 responseType: 'blob' 被静默丢弃，
// 文件下载按默认 JSON 解析响应（CSV/ZIP 文本以 string 形式返回），
// 调用方 URL.createObjectURL(string) 直接抛 "Overload resolution failed"。
export const rawAxios = axios.create({ baseURL: API_BASE, timeout: 30000 })

rawAxios.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('pc_admin_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  if (!cfg.headers['X-Device-Id']) cfg.headers['X-Device-Id'] = getDeviceId()
  return cfg
})

export function requestRaw(config) {
  const { method = 'GET', url, params, data, headers, responseType } = config
  return rawAxios.request({ method, url, params, data, headers, responseType })
}
