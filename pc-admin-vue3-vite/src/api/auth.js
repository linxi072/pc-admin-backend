import { request } from './http'

// GET /api/auth/captcha -> CaptchaView { token, question }
// 后端始终提供该端点；后端 auth.captcha.enabled 控制登录是否强制校验，
// 前端始终展示并随登录回传，enabled=false 时后端自动忽略（安全且兼容）。
export const getCaptcha = () =>
  request({ method: 'GET', url: '/api/auth/captcha' })

// POST /api/auth/login -> TokenView { accessToken, refreshToken, expiresIn }
// captchaToken / captchaAnswer 为验证码凭据；未启用验证码时后端忽略这两个字段。
export const login = (username, password, captchaToken, captchaAnswer) =>
  request({
    method: 'POST',
    url: '/api/auth/login',
    data: { username, password, captchaToken, captchaAnswer }
  })

// POST /api/auth/logout
export const logout = () => request({ method: 'POST', url: '/api/auth/logout' })

// POST /api/auth/change-password -> 会话无关改密（用户名+原密码+新密码，无需已登录会话）
// 用于登录页「修改密码」入口，以及被「请修改初始/重置密码后再登录」(AUTH_014) 拦截时的自救通道
export const changePassword = (username, oldPassword, newPassword) =>
  request({
    method: 'POST',
    url: '/api/auth/change-password',
    data: { username, oldPassword, newPassword }
  })

// POST /api/auth/forgot-password -> 忘记密码自助重置（用户名+手机号/邮箱核验+新密码，无需原密码）
// mobile/email 至少提供一个，须与账号登记信息匹配；服务端对「用户不存在」与
// 「验证信息不匹配」返回同一错误（防账号枚举）
export const forgotPassword = (username, newPassword, mobile, email) =>
  request({
    method: 'POST',
    url: '/api/auth/forgot-password',
    data: { username, newPassword, mobile, email }
  })
