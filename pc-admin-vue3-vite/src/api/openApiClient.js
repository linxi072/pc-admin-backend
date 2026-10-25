import { request } from './http'

// 开放平台客户端注册与密钥治理（V48 / M24）：「外部系统如何接入本平台」的治理面。
// 后端 base-path：/api/system/openapi-clients
// 权限：查询类 system:openapi-client:read；写操作 system:openapi-client:manage
// 密钥语义：明文仅在创建/轮换成功时返回一次（plainSecret），服务端只存哈希，查询不回放；
// 任何查询响应都不含密钥字段。离线约束：授权服务器依赖不在离线仓库，暂无 token 端点。

/** 分页查询（支持 clientId 模糊 / status 筛选，服务端排序白名单）。 */
export const pageOpenApiClients = (params) =>
  request({ method: 'GET', url: '/api/system/openapi-clients/page', params })

/** 查询全部客户端（含停用）。 */
export const listOpenApiClients = () =>
  request({ method: 'GET', url: '/api/system/openapi-clients/list' })

/** 查询单个客户端。 */
export const getOpenApiClient = (id) =>
  request({ method: 'GET', url: `/api/system/openapi-clients/${id}` })

/**
 * 注册客户端。body: { clientId, clientName, scopes?, status?, expiredAt?, remark? }
 * 返回 { clientId, plainSecret, rotatedAt }——明文密钥仅此一次可见，须立即保存。
 */
export const createOpenApiClient = (body) =>
  request({ method: 'POST', url: '/api/system/openapi-clients', data: body })

/** 更新客户端（部分更新；client_id 不可变更，密钥变更走轮换）。 */
export const updateOpenApiClient = (id, body) =>
  request({ method: 'PUT', url: `/api/system/openapi-clients/${id}`, data: body })

/** 删除客户端（逻辑删除）。 */
export const deleteOpenApiClient = (id) =>
  request({ method: 'DELETE', url: `/api/system/openapi-clients/${id}` })

/** 启停客户端。enabled 为布尔。 */
export const toggleOpenApiClient = (id, enabled) =>
  request({
    method: 'PUT',
    url: `/api/system/openapi-clients/${id}/toggle`,
    params: { enabled }
  })

/**
 * 轮换密钥：旧密钥立即失效，返回新明文密钥（仅此一次可见）。
 * 返回 { clientId, plainSecret, rotatedAt }。
 */
export const rotateOpenApiClientSecret = (id) =>
  request({ method: 'POST', url: `/api/system/openapi-clients/${id}/rotate-secret` })
