import { describe, expect, it, vi } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  createCategory,
  updateCategory,
  deleteCategory,
  getCategory,
  listCategories,
  treeCategories,
  createArticle,
  updateArticle,
  deleteArticle,
  getArticle,
  publishArticle,
  offlineArticle,
  viewArticle,
  pageArticles,
  listArticleVersions,
  rollbackArticle,
  createArticlePreviewToken,
  batchMoveArticles,
  pageRecycleArticles,
  restoreArticle,
  batchRestoreArticles,
  purgeArticle,
  reorderArticles
} from '../cms.js'

// C8：锁定 CMS 前后端 22 个管理端点的 URL / 方法 / 参数同形（含 C9 回收站 4 个 + C10 排序 1 个）。
// URL 断言与 src/api/cms.js 实现保持一致；契约扫描器已排除 src/api/tests/ 夹具目录。
// 公开端点 /api/cms/preview/{token} 与 /api/cms/public/articles 均为 feature-gate 默认关闭的
// 外部 API（分别受 cms.preview.enabled / cms.public.enabled 控制），前端管理端不消费，属豁免。

const catId = 9
const articleId = 101

describe('CMS 栏目 API 契约', () => {
  it('栏目 CRUD 端点与后端一一对应', () => {
    const body = { categoryCode: 'news', categoryName: '新闻' }
    createCategory(body)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/cms/category`, data: body })
    updateCategory(body)
    expect(request).toHaveBeenCalledWith({ method: 'PUT', url: `/api/cms/category`, data: body })
    deleteCategory(catId)
    expect(request).toHaveBeenCalledWith({ method: 'DELETE', url: `/api/cms/category/${catId}` })
    getCategory(catId)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/cms/category/${catId}` })
    listCategories()
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/cms/category/list` })
    treeCategories()
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/cms/category/tree` })
  })
})

describe('CMS 文章 API 契约', () => {
  it('文章 CRUD / 发布 / 下线 / 浏览端点同形', () => {
    const body = { categoryId: 7, title: '标题' }
    createArticle(body)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/cms/article`, data: body })
    updateArticle(body)
    expect(request).toHaveBeenCalledWith({ method: 'PUT', url: `/api/cms/article`, data: body })
    deleteArticle(articleId)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/cms/article/${articleId}/delete` })
    getArticle(articleId)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/cms/article/${articleId}` })
    publishArticle(articleId)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/cms/article/${articleId}/publish` })
    offlineArticle(articleId)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/cms/article/${articleId}/offline` })
    viewArticle(articleId)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/cms/article/${articleId}/view` })
  })

  it('分页查询透传筛选参数', () => {
    const query = { page: 2, size: 20, categoryId: 7, title: '关键', status: 'PUBLISHED' }
    pageArticles(query)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/cms/article/page`, params: query })
  })
})

describe('CMS 迭代二新增端点契约', () => {
  it('版本列表与回滚端点同形（C4）', () => {
    listArticleVersions(articleId)
    expect(request).toHaveBeenCalledWith({
      method: 'GET',
      url: `/api/cms/article/${articleId}/versions`
    })
    rollbackArticle(articleId, 3)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/${articleId}/rollback`,
      params: { versionNo: 3 }
    })
  })

  it('预览令牌生成端点同形（C6）', () => {
    createArticlePreviewToken(articleId)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/${articleId}/preview-token`,
      params: { ttlHours: undefined }
    })
    createArticlePreviewToken(articleId, 48)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/${articleId}/preview-token`,
      params: { ttlHours: 48 }
    })
  })

  it('批量移动端点同形（ids + categoryId 走请求体）', () => {
    batchMoveArticles([101, 102], 7)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/batch-move`,
      data: { ids: [101, 102], categoryId: 7 }
    })
  })

  it('回收站分页/恢复/批量恢复/彻底删除端点同形（C9）', () => {
    const query = { page: 1, size: 20, title: '关键' }
    pageRecycleArticles(query)
    expect(request).toHaveBeenCalledWith({
      method: 'GET',
      url: `/api/cms/article/recycle/page`,
      params: query
    })
    restoreArticle(articleId)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/${articleId}/restore`
    })
    batchRestoreArticles([101, 102])
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/batch-restore`,
      data: { ids: [101, 102] }
    })
    purgeArticle(articleId)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/cms/article/${articleId}/purge`
    })
  })

  it('批量排序端点同形（items 数组走请求体，C10）', () => {
    const items = [{ id: 101, sortNo: 5 }, { id: 102, sortNo: 2 }]
    reorderArticles(items)
    expect(request).toHaveBeenCalledWith({
      method: 'PUT',
      url: `/api/cms/article/sort`,
      data: items
    })
  })

  it('不使用 blob 通道，全部走统一 request', () => {
    expect(requestRaw).not.toHaveBeenCalled()
  })
})
