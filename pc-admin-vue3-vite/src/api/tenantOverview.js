import { request } from './http'

/**
 * GET /api/system/tenants/overview -> TenantOverviewView
 * 跨租户运营总览（只读聚合）：enforce 开关 / 租户清单与启用数 / 每租户数据量 / 存量隔离自检结论。
 * 权限 system:tenant:read（与「租户管理」列表同源）。
 */
export const getTenantOverview = () =>
  request({ method: 'GET', url: '/api/system/tenants/overview' })
