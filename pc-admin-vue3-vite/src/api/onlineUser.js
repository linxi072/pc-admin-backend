import { request } from './http'

// GET /api/system/online-users/page?page&size&username&ip&status -> PageResult<OnlineUserView>
// status 为会话展示态筛选：ONLINE / IDLE / RESTRICTED，空为全部
export const pageOnlineUsers = (query) =>
  request({ method: 'GET', url: '/api/system/online-users/page', params: query })

// GET /api/system/online-users/restrictions/page?page&size&username&status -> PageResult<LoginRestrictionView>
// status：ACTIVE / RELEASED，空为全部
export const pageRestrictions = (query) =>
  request({ method: 'GET', url: '/api/system/online-users/restrictions/page', params: query })

// POST /api/system/online-users/force-logout -> Integer（下线会话数）
// 吊销目标会话对应设备的刷新令牌并置会话离线，设备侧下一个请求即 401
export const forceLogout = (sessionIds) =>
  request({ method: 'POST', url: '/api/system/online-users/force-logout', data: { sessionIds } })

// POST /api/system/online-users/users/{userId}/restrict -> String（限制记录ID）
// durationMinutes 白名单：30/60/720/1440/4320/10080/0（长期）；reason 必填
export const restrictLogin = (userId, body) =>
  request({ method: 'POST', url: `/api/system/online-users/users/${userId}/restrict`, data: body })

// POST /api/system/online-users/users/{userId}/release -> Integer（解除条数，幂等）
export const releaseRestriction = (userId) =>
  request({ method: 'POST', url: `/api/system/online-users/users/${userId}/release` })
