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
