import { request } from './http'

// 脱敏策略中心（V46）：为 M1 审计/M20 增强等既有脱敏点提供「按字段键 + 按角色」的统一策略配置面。
// 后端 base-path：/api/system/mask-strategies
// 权限：查询类 system:mask-strategy:read；写操作 system:mask-strategy:manage
// 字段说明：enabled 服务端存 0/1，前端统一用布尔展示；exemptRoles 为逗号/分号分隔的 role_code 列表；
// sensitiveType 取值与后端 SensitiveType 枚举一致（NONE 仅作显式放行标志，不在下拉可选之列）。

/** 分页查询（支持 fieldKey / sensitiveType / enabled 筛选，服务端排序白名单）。 */
export const pageMaskStrategies = (params) =>
  request({ method: 'GET', url: '/api/system/mask-strategies/page', params })

/** 查询全部脱敏策略（含停用）。 */
export const listMaskStrategies = () =>
  request({ method: 'GET', url: '/api/system/mask-strategies/list' })

/** 查询单个脱敏策略。 */
export const getMaskStrategy = (id) =>
  request({ method: 'GET', url: `/api/system/mask-strategies/${id}` })

/** 创建脱敏策略。body 见 CreateMaskStrategyRequest（applyScope/enabled 可选，默认 ROLE_EXEMPT/启用）。 */
export const createMaskStrategy = (body) =>
  request({ method: 'POST', url: '/api/system/mask-strategies', data: body })

/** 更新脱敏策略（部分更新，仅传入字段参与）。body 见 UpdateMaskStrategyRequest。 */
export const updateMaskStrategy = (id, body) =>
  request({ method: 'PUT', url: `/api/system/mask-strategies/${id}`, data: body })

/** 删除脱敏策略（逻辑删除）。 */
export const deleteMaskStrategy = (id) =>
  request({ method: 'DELETE', url: `/api/system/mask-strategies/${id}` })

/** 启停脱敏策略。enabled 为布尔。 */
export const toggleMaskStrategy = (id, enabled) =>
  request({
    method: 'PUT',
    url: `/api/system/mask-strategies/${id}/toggle`,
    params: { enabled }
  })

/** 刷新解析缓存（全量失效，下次命中重新加载）；返回被失效的缓存条目数。 */
export const refreshMaskStrategyCache = () =>
  request({ method: 'POST', url: '/api/system/mask-strategies/refresh-cache' })
