import { request } from './http'

// GET /api/auth/captcha -> CaptchaView { token, question }
// 后端始终提供该端点；后端 auth.captcha.enabled 控制登录是否强制校验，
// 前端始终展示并随登录回传，enabled=false 时后端自动忽略（安全且兼容）。
export const getCaptcha = () =>
  request({ method: 'GET', url: '/api/auth/captcha' })

// POST /api/auth/login -> TokenView { accessToken, tokenType, expiresIn }
// captchaToken / captchaAnswer 为验证码凭据；未启用验证码时后端忽略这两个字段。
export const login = (username, password, captchaToken, captchaAnswer) =>
  request({
    method: 'POST',
    url: '/api/auth/login',
    data: { username, password, captchaToken, captchaAnswer }
  })

// POST /api/auth/logout
export const logout = () => request({ method: 'POST', url: '/api/auth/logout' })
