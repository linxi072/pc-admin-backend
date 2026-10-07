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
