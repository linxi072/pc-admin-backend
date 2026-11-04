import { request } from './http'

// ==================== 看板模板管理（DashboardTemplateController /api/dashboard/template） ====================

// POST / -> Long（创建默认 DRAFT 草稿态）
// body: { templateCode, templateName, description?, layoutJson?（合法 JSON 字符串）, status?='DRAFT' }
export const createDashboardTemplate = (body) =>
  request({ method: 'POST', url: '/api/dashboard/template', data: body })

// PUT / -> void（按 id 选择性更新，null 字段不覆盖）
export const updateDashboardTemplate = (body) =>
  request({ method: 'PUT', url: '/api/dashboard/template', data: body })

// POST /{id}/delete -> void
export const deleteDashboardTemplate = (id) =>
  request({ method: 'POST', url: `/api/dashboard/template/${id}/delete` })

// GET /{id} -> DashboardTemplateVO
export const getDashboardTemplate = (id) =>
  request({ method: 'GET', url: `/api/dashboard/template/${id}` })

// POST /{id}/publish -> void（DRAFT/OFFLINE → PUBLISHED）
export const publishDashboardTemplate = (id) =>
  request({ method: 'POST', url: `/api/dashboard/template/${id}/publish` })

// POST /{id}/unpublish -> void（PUBLISHED → OFFLINE）
export const unpublishDashboardTemplate = (id) =>
  request({ method: 'POST', url: `/api/dashboard/template/${id}/unpublish` })

// POST /{id}/view -> void（浏览计数 +1）
export const viewDashboardTemplate = (id) =>
  request({ method: 'POST', url: `/api/dashboard/template/${id}/view` })

// GET /page?page&size&templateCode&templateName&status&sortField&sortDirection -> PageResult<DashboardTemplateVO>
export const pageDashboardTemplates = (query) =>
  request({ method: 'GET', url: '/api/dashboard/template/page', params: query })
