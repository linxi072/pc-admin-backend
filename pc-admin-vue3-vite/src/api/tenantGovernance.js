import { request } from './http'

// 租户治理通道（M19 正式立项）：跨租户数据总览 / 存量归位 / 跨租户迁移，权限 system:tenant:manage。
// 统一经拆包层发出；注释不含 URL，以免干扰契约测试计数。

// 跨租户数据总览：按租户聚合各业务表行数
export const fetchTenantDataOverview = () =>
  request({ method: 'GET', url: '/api/system/tenants/data-overview' })

// 存量数据归位到目标租户（历史数据 tenant_id 改写）
export const backfillTenantData = (body) =>
  request({ method: 'POST', url: '/api/system/tenants/backfill', data: body })

// 跨租户数据迁移（tables 空表示全部业务表）
export const reassignTenantData = (body) =>
  request({ method: 'POST', url: '/api/system/tenants/reassign', data: body })
