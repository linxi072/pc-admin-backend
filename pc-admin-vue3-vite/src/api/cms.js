import { request } from './http'

// ==================== 栏目管理 ====================

export const createCategory = (body) => request({ method: 'POST', url: '/api/cms/category', data: body })

export const updateCategory = (body) => request({ method: 'PUT', url: '/api/cms/category', data: body })

export const deleteCategory = (id) => request({ method: 'DELETE', url: `/api/cms/category/${id}` })

export const getCategory = (id) => request({ method: 'GET', url: `/api/cms/category/${id}` })

export const listCategories = () => request({ method: 'GET', url: '/api/cms/category/list' })

export const treeCategories = () => request({ method: 'GET', url: '/api/cms/category/tree' })

// ==================== 文章管理 ====================

export const createArticle = (body) => request({ method: 'POST', url: '/api/cms/article', data: body })

export const updateArticle = (body) => request({ method: 'PUT', url: '/api/cms/article', data: body })

export const deleteArticle = (id) => request({ method: 'POST', url: `/api/cms/article/${id}/delete` })

export const getArticle = (id) => request({ method: 'GET', url: `/api/cms/article/${id}` })

export const publishArticle = (id) => request({ method: 'POST', url: `/api/cms/article/${id}/publish` })

export const offlineArticle = (id) => request({ method: 'POST', url: `/api/cms/article/${id}/offline` })

export const viewArticle = (id) => request({ method: 'POST', url: `/api/cms/article/${id}/view` })

export const pageArticles = (query) => request({ method: 'GET', url: '/api/cms/article/page', params: query })

/** 批量移动文章到目标栏目（ids 最多 200 篇，服务端事务原子执行）。 */
export const batchMoveArticles = (ids, categoryId) =>
  request({ method: 'POST', url: '/api/cms/article/batch-move', data: { ids, categoryId } })

// ==================== 文章版本历史（C4） ====================

export const listArticleVersions = (id) => request({ method: 'GET', url: `/api/cms/article/${id}/versions` })

export const rollbackArticle = (id, versionNo) =>
  request({ method: 'POST', url: `/api/cms/article/${id}/rollback`, params: { versionNo } })

// ==================== 草稿预览令牌（C6） ====================

export const createArticlePreviewToken = (id, ttlHours) =>
  request({ method: 'POST', url: `/api/cms/article/${id}/preview-token`, params: { ttlHours } })

// 注：/api/cms/preview/{token} 为 feature-gate 默认关闭的公开端点（cms.preview.enabled），
// 供外部免登录预览，前端管理端不消费，故不在本模块登记同形函数（api-contract 豁免）。
