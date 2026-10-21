import { request } from './http'

// GET /api/system/posts/page?page&size&keyword&status&sortField&sortDirection -> PageResult<PostView>
export const pagePosts = (query) =>
  request({ method: 'GET', url: '/api/system/posts/page', params: query })

// GET /api/system/posts/options -> List<PostOptionVO>（全部启用岗位，供用户表单等多选）
export const listPostOptions = () =>
  request({ method: 'GET', url: '/api/system/posts/options' })

// POST /api/system/posts -> Long
export const createPost = (body) =>
  request({ method: 'POST', url: '/api/system/posts', data: body })

// PUT /api/system/posts/{id} -> void
export const updatePost = (id, body) =>
  request({ method: 'PUT', url: `/api/system/posts/${id}`, data: body })

// DELETE /api/system/posts/{id} -> void（逻辑删除，并清理用户关联）
export const deletePost = (id) =>
  request({ method: 'DELETE', url: `/api/system/posts/${id}` })

// PUT /api/system/posts/users/{userId} -> void（整体替换用户岗位，空列表清空）
export const assignUserPosts = (userId, body) =>
  request({ method: 'PUT', url: `/api/system/posts/users/${userId}`, data: body })
