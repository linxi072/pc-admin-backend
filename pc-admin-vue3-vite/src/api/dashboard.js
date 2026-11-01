import { request } from './http'

/**
 * GET /api/system/dashboard/overview -> DashboardOverview
 *
 * 运营总览快照：系统域计数（租户 / 用户 / 角色 / 菜单 / 在线会话 / 幂等记录 /
 * 操作日志 / 今日登录）外加跨模块可观测指标（N+1 疑似、链路追踪）。
 * 任一统计查询失败静默回落（计数类 0、可观测回落 EMPTY），看板不整体报错。
 */
export const dashboardOverview = () =>
  request({ method: 'GET', url: '/api/system/dashboard/overview' })

/**
 * GET /api/system/dashboard/trend?days=30 -> DashboardTrend
 *
 * 近 N 日时间序列（新增用户 / 新增租户 / 活跃租户 / 登录日志 / 操作日志 / 通知发送），
 * 缺数据日已补齐为 0，前端可直接按索引渲染。days 由后端钳制到 [1, 90]。
 * @param {number} days 回溯天数
 */
export const dashboardTrend = (days) =>
  request({ method: 'GET', url: '/api/system/dashboard/trend', params: { days } })

/**
 * GET /api/system/dashboard/drill -> DashboardDrillVO
 *
 * 运营看板下钻：返回指定指标在指定日期的逐小时分布（0–23 点）。
 * @param {('NEW_USERS'|'NEW_TENANTS'|'ACTIVE_TENANTS'|'LOGIN_LOGS'|'OPERATION_LOGS'|'NOTIFICATIONS')} metric 下钻指标
 * @param {string} [date] 下钻日期（YYYY-MM-DD，缺省回落为今日）
 */
export const dashboardDrill = (metric, date) =>
  request({
    method: 'GET',
    url: '/api/system/dashboard/drill',
    params: date ? { metric, date } : { metric }
  })
