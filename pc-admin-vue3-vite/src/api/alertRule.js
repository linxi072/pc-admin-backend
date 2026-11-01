import { request } from './http'

// 告警规则管理（CRUD / 启停 / 立即评估）。
// 契约来源：monitor/alert/controller/AlertRuleController.java。
// 读取权限 system:monitor:read；写操作（增删改/启停/评估）权限 system:monitor:rule:manage。
// 由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动登记权限码并授予 SUPER_ADMIN。

// GET /api/monitoring/alert-rules -> AlertRuleView[]
export const listAlertRules = (params) =>
  request({ method: 'GET', url: '/api/monitoring/alert-rules', params })

// GET /api/monitoring/alert-rules/{id} -> AlertRuleView
export const getAlertRule = (id) =>
  request({ method: 'GET', url: `/api/monitoring/alert-rules/${id}` })

// POST /api/monitoring/alert-rules -> AlertRuleView
export const createAlertRule = (data) =>
  request({ method: 'POST', url: '/api/monitoring/alert-rules', data })

// PUT /api/monitoring/alert-rules/{id} -> AlertRuleView
export const updateAlertRule = (id, data) =>
  request({ method: 'PUT', url: `/api/monitoring/alert-rules/${id}`, data })

// DELETE /api/monitoring/alert-rules/{id} -> void
export const deleteAlertRule = (id) =>
  request({ method: 'DELETE', url: `/api/monitoring/alert-rules/${id}` })

// POST /api/monitoring/alert-rules/{id}/toggle?enabled= -> AlertRuleView
export const toggleAlertRule = (id, enabled) =>
  request({ method: 'POST', url: `/api/monitoring/alert-rules/${id}/toggle`, params: { enabled } })

// POST /api/monitoring/alert-rules/evaluate -> Integer（本次新触发条数）
export const evaluateAlertRules = () =>
  request({ method: 'POST', url: '/api/monitoring/alert-rules/evaluate' })
