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
