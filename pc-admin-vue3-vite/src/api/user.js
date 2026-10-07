import { request } from './http'

// GET /api/system/users/page?page&size&username&status&orgId -> PageResult<UserView>
export const pageUsers = (query) =>
  request({ method: 'GET', url: '/api/system/users/page', params: query })

// GET /api/system/users/{id} -> UserView
export const getUser = (id) =>
  request({ method: 'GET', url: `/api/system/users/${id}` })

// POST /api/system/users -> Long
export const createUser = (body) =>
  request({ method: 'POST', url: '/api/system/users', data: body })

// PUT /api/system/users/{id} -> void
export const updateUser = (id, body) =>
  request({ method: 'PUT', url: `/api/system/users/${id}`, data: body })

// DELETE /api/system/users/{id} -> void（物理删除 / 硬删除）
export const deleteUser = (id) =>
  request({ method: 'DELETE', url: `/api/system/users/${id}` })

// POST /api/system/users/{id}/reset-password -> void
export const resetPassword = (id, password) =>
  request({
    method: 'POST',
    url: `/api/system/users/${id}/reset-password`,
    data: { password }
  })
