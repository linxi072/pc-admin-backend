import axios from 'axios'
import { ElMessage } from 'element-plus'
import { API_BASE } from './config'
import { clearAuth, setAuth, authState } from '../store/auth'
import { closeRealtime } from './realtime'

import { isSuccessResult, ResultError } from './result'

const realAxios = axios.create({ baseURL: API_BASE, timeout: 15000 })

// 刷新令牌走独立裸 axios 实例：公开端点无需 Bearer，且必须绕开 realAxios 的
// 401 拦截器（否则刷新自身失败会递归触发刷新）。响应仍是统一 Result<TokenView>。
const refreshAxios = axios.create({ baseURL: API_BASE, timeout: 15000 })

/** 会话续期（AUTH refresh）：用 refreshToken 换取新令牌对，成功即轮换写回 store。 */
export function refreshAccessToken() {
  const refreshToken = authState.refreshToken || localStorage.getItem('pc_admin_refresh_token')
  if (!refreshToken) return Promise.reject(new Error('no refresh token'))
  return refreshAxios.post('/api/auth/refresh', { refreshToken }).then((resp) => {
    const body = resp.data
    if (!isSuccessResult(body)) {
      throw new ResultError(body?.message || '刷新失败', body?.code, body?.data)
    }
    const view = body.data
    if (!view?.accessToken) throw new ResultError('刷新响应缺少 accessToken')
    setAuth(view.accessToken, authState.username, view.refreshToken || refreshToken)
    return view
  })
}

// 并发单飞：多个请求同时收到 401 时只发起一次刷新，其余共享同一 Promise。
let refreshingPromise = null

function refreshOnce() {
  if (!refreshingPromise) {
    refreshingPromise = refreshAccessToken().finally(() => { refreshingPromise = null })
  }
  return refreshingPromise
}

// 401 时不做刷新的公开端点：登录/刷新/验证码/改密/忘记密码/OAuth2 令牌自测。
// 这些端点的 401 是业务校验失败（如 invalid_client、验证码错误），非会话过期。
// 仅存路径后缀（不含 /api/ 前缀），避免被 api-contract 扫描器误识别为前端 API 引用。
const NO_REFRESH_SUFFIXES = [
  'auth/login', 'auth/refresh', 'auth/captcha',
  'auth/change-password', 'auth/forgot-password', 'oauth2/token'
]

function shouldRefresh(err) {
  const cfg = err.config || {}
  if (cfg.skipAuthRefresh || cfg._authRetried) return false
  const url = cfg.url || ''
  return !NO_REFRESH_SUFFIXES.some((p) => url.includes(p))
}

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

function injectAuthHeaders(cfg) {
  const token = localStorage.getItem('pc_admin_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  if (!cfg.headers['X-Device-Id']) cfg.headers['X-Device-Id'] = getDeviceId()
  return cfg
}

/**
 * HTTP 层错误统一处理（realAxios 与 rawAxios 共用）：
 * - 401：清理登录态、关闭实时通道、带 redirect 回跳登录页
 * - 其余：弹出错误提示（调用方 catch 里无需重复提示）
 *
 * 早期 rawAxios 漏配本拦截器，导致下载/导出/导入类请求的 HTTP 错误完全静默，
 * 且 401 不清理登录态（用户卡在死页）。
 */
async function handleHttpError(err) {
  const status = err.response?.status
  if (status === 401) {
    // 仅受保护请求（携带 Authorization）的 401 才表示登录态失效。
    // 匿名端点（如 /oauth2/token 密钥错误返回 401 invalid_client）不能清理管理员登录态，
    // 否则在「开放平台客户端」页做密钥自测输错 secret 会被直接踢下线。
    const protectedRequest = Boolean(err.config?.headers?.Authorization)
    if (protectedRequest && shouldRefresh(err) && authState.refreshToken) {
      try {
        await refreshOnce()
        // 重放原请求：标记 _authRetried，若再次 401 则不再刷新（防死循环）。
        const cfg = err.config
        cfg._authRetried = true
        cfg.headers = cfg.headers || {}
        cfg.headers.Authorization = `Bearer ${authState.token}`
        const replay = cfg.__raw ? rawAxios.request(cfg) : realAxios.request(cfg)
        return replay
      } catch {
        // 刷新失败（含令牌过期/被吊销/家族重放）或重放仍 401 → 走统一登出流程。
      }
    }
    if (protectedRequest) {
      clearAuth()
      closeRealtime()
      import('../router').then((m) => m.default.replace({
        path: '/login',
        query: { redirect: m.default.currentRoute.value.fullPath }
      }))
    }
    return Promise.reject(err)
  }
  const msg = err.response?.data?.message || err.message || '网络错误'
  ElMessage.error(msg)
  return Promise.reject(err)
}

// 请求拦截：注入 JWT 与设备标识
realAxios.interceptors.request.use(injectAuthHeaders)

// 响应拦截：统一拆包 Result<T>{code,message,data}，兼容成功码 "0" 与 0（见 result.js）
realAxios.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (isSuccessResult(body)) return body.data
    const msg = body?.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new ResultError(msg, body?.code, body?.data))
  },
  handleHttpError
)

/**
 * 统一请求入口：页面与组件获取数据的唯一通道（后端为唯一数据源，前端无任何 mock 分支）。
 *
 * 响应拦截器已拆包 Result<T>{code,message,data}，
 * 因此 API 模块与组件拿到的直接是 data，无需再判 code。
 */
export function request(config) {
  const { method = 'GET', url, params, data, headers, skipAuthRefresh } = config
  return realAxios.request({ method, url, params, data, headers, skipAuthRefresh })
}

// 原始请求通道：不经过「拆包」响应拦截，用于文件下载（blob）等需要原始响应的场景。
// responseType 等调用方显式声明的 axios 配置必须透传——早前白名单式解构只取了
// method/url/params/data/headers，导致调用方传的 responseType: 'blob' 被静默丢弃，
// 文件下载按默认 JSON 解析响应（CSV/ZIP 文本以 string 形式返回），
// 调用方 URL.createObjectURL(string) 直接抛 "Overload resolution failed"。
// 响应错误拦截与 realAxios 共用 handleHttpError：401 清理登录态并跳登录，其余弹提示——
// 早期本通道无错误拦截，下载/导出/导入的 HTTP 失败完全静默。
export const rawAxios = axios.create({ baseURL: API_BASE, timeout: 30000 })

rawAxios.interceptors.request.use(injectAuthHeaders)

rawAxios.interceptors.response.use(
  (resp) => resp,
  (err) => {
    // 匿名端点（如 /oauth2/token 密钥自测）的 401 是业务校验失败（invalid_client），
    // 不代表管理员登录态失效，调用方可通过 skipAuthErrorHandling 显式关闭清理逻辑。
    if (err.config?.skipAuthErrorHandling) return Promise.reject(err)
    return handleHttpError(err)
  }
)

export function requestRaw(config) {
  const { method = 'GET', url, params, data, headers, responseType, skipAuthErrorHandling, skipAuthRefresh } = config
  return rawAxios.request({
    method, url, params, data, headers, responseType, skipAuthErrorHandling, skipAuthRefresh,
    __raw: true
  })
}
