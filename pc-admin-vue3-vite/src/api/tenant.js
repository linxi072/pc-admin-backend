import { request } from './http'

/**
 * GET /api/system/tenants/page -> PageResult<TenantView>
 * 分页查询租户（支持 tenantCode 精确 / status 筛选）。
 */
export const tenantPage = (params) =>
  request({ method: 'GET', url: '/api/system/tenants/page', params })

/**
 * GET /api/system/tenants/list -> TenantView[]
 * 查询全部租户（含停用），供选择器使用。
 */
export const tenantList = () =>
  request({ method: 'GET', url: '/api/system/tenants/list' })

/**
 * GET /api/system/tenants/{id} -> TenantView
 */
export const getTenant = (id) =>
  request({ method: 'GET', url: `/api/system/tenants/${id}` })

/**
 * POST /api/system/tenants -> Long（新租户 ID）
 * 注册租户（tenant_code 创建后不可变更）。
 */
export const createTenant = (data) =>
  request({ method: 'POST', url: '/api/system/tenants', data })

/**
 * PUT /api/system/tenants/{id} -> void
 * 更新租户（部分更新；tenant_code 不可变更）。
 */
export const updateTenant = (id, data) =>
  request({ method: 'PUT', url: `/api/system/tenants/${id}`, data })

/**
 * DELETE /api/system/tenants/{id} -> void
 * 删除租户（逻辑删除）。
 */
export const removeTenant = (id) =>
  request({ method: 'DELETE', url: `/api/system/tenants/${id}` })

/**
 * PUT /api/system/tenants/{id}/toggle?enabled= -> void
 * 启停租户（ENABLED / DISABLED）。
 */
export const toggleTenant = (id, enabled) =>
  request({ method: 'PUT', url: `/api/system/tenants/${id}/toggle`, params: { enabled } })

/**
 * POST /api/system/tenants/{id}/provision -> TenantProvisioningView
 * 开通租户（初始化管理员 / 角色 / 部门），仅平台管理员可调用。
 */
export const provisionTenant = (id, data) =>
  request({ method: 'POST', url: `/api/system/tenants/${id}/provision`, data })

/**
 * GET /api/system/tenants/data-overview -> TenantDataOverview[]
 * 跨租户数据总览：按租户聚合各业务表行数（平台管理员跨租户旁路）。
 */
export const tenantDataOverview = () =>
  request({ method: 'GET', url: '/api/system/tenants/data-overview' })
