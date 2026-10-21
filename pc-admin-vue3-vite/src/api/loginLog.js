import { request } from './http'

/**
 * GET /api/system/login-logs/page -> PageResult<LoginLogView>
 *
 * 登录日志分页（V29 登录日志页）。筛选：用户名/结果/登录类型/失败码/IP/关键字/时间区间；
 * 时间区间缺省收敛到最近 7 天（服务端口径）。
 */
export const pageLoginLogs = (query) =>
  request({ method: 'GET', url: '/api/system/login-logs/page', params: query })

/**
 * GET /api/system/login-logs/summary -> { total, success, failure, failureRate, distinctUsers, lastLoginAt }
 *
 * 登录日志统计摘要（与 page 相同筛选口径）。
 */
export const loginLogSummary = (params) =>
  request({ method: 'GET', url: '/api/system/login-logs/summary', params })
