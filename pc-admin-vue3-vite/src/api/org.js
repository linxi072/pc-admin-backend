import { request } from './http'

// GET /api/system/orgs/tree -> List<OrgTreeVO>
export const orgTree = () =>
  request({ method: 'GET', url: '/api/system/orgs/tree' })

// POST /api/system/orgs -> Long (CreateOrgRequest: parentId, orgCode, orgName, orgType, sortNo, leaderUserId, status)
export const createOrg = (data) =>
  request({ method: 'POST', url: '/api/system/orgs', data })

// PUT /api/system/orgs/{id} -> Void (UpdateOrgRequest: 同上，parentId 变更时后端重算 ancestors 物化路径并防环)
export const updateOrg = (id, data) =>
  request({ method: 'PUT', url: `/api/system/orgs/${id}`, data })

// DELETE /api/system/orgs/{id}
export const deleteOrg = (id) =>
  request({ method: 'DELETE', url: `/api/system/orgs/${id}` })
