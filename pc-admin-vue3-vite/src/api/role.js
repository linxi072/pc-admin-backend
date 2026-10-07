import { request } from './http'

// GET /api/system/roles -> List<RoleView>
export const listRoles = () =>
  request({ method: 'GET', url: '/api/system/roles' })

// GET /api/system/roles/{id} -> RoleView（含 menuIds / apiIds）
export const getRole = (id) =>
  request({ method: 'GET', url: `/api/system/roles/${id}` })

// POST /api/system/roles -> Long
export const createRole = (body) =>
  request({ method: 'POST', url: '/api/system/roles', data: body })

// PUT /api/system/roles/{id} -> void
export const updateRole = (id, body) =>
  request({ method: 'PUT', url: `/api/system/roles/${id}`, data: body })

// DELETE /api/system/roles/{id} -> void
export const deleteRole = (id) =>
  request({ method: 'DELETE', url: `/api/system/roles/${id}` })

// GET /api/system/roles/{id}/data-scopes -> List<DataScopeRuleView>
// DataScopeRuleView: { id, roleId, resourceCode, scopeType, orgIds, orgNames }
// scopeType ∈ ALL | SELF | DEPT | DEPT_AND_CHILD | CUSTOM
export const listRoleDataScopes = (id) =>
  request({ method: 'GET', url: `/api/system/roles/${id}/data-scopes` })

// PUT /api/system/roles/{id}/data-scopes -> void（覆盖式保存）
// body: { resourceCode, scopeType, orgIds }；scopeType=CUSTOM 时 orgIds 必填，其余须为空
export const saveRoleDataScope = (id, body) =>
  request({ method: 'PUT', url: `/api/system/roles/${id}/data-scopes`, data: body })
