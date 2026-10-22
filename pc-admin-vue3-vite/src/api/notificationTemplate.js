import { request } from './http'

// 通知模板中心（V41）：为 M6 多渠道通知（IN_APP / EMAIL / SMS / WEBHOOK / WEBSOCKET）提供可复用模板样板。
// 后端 base-path：/api/system/notification-templates
// 权限：查询类 system:notification-template:read；写操作 system:notification-template:manage
// 字段说明：enabled 服务端存 0/1，前端统一用布尔展示；content 支持 ${var} 占位，由 preview 接口渲染。

/** 分页查询（支持 channel / keyword / enabled 筛选，服务端排序白名单）。 */
export const pageNotificationTemplates = (params) =>
  request({ method: 'GET', url: '/api/system/notification-templates/page', params })

/** 查询单个模板（含变量名解析）。 */
export const getNotificationTemplate = (id) =>
  request({ method: 'GET', url: `/api/system/notification-templates/${id}` })

/** 获取模板变量名列表（${var} 占位解析）。 */
export const getTemplateVariables = (id) =>
  request({ method: 'GET', url: `/api/system/notification-templates/${id}/variables` })

/**
 * 渲染预览：用变量映射渲染正文，返回 rendered / requiredVariables / missingVariables。
 * body: { variables: { 变量名: 取值 } }
 */
export const previewNotificationTemplate = (id, variables = {}) =>
  request({
    method: 'POST',
    url: `/api/system/notification-templates/${id}/preview`,
    data: { variables }
  })

/** 创建模板。body 见 CreateNotificationTemplateRequest（enabled 可选，默认启用）。 */
export const createNotificationTemplate = (body) =>
  request({ method: 'POST', url: '/api/system/notification-templates', data: body })

/** 更新模板（部分更新，仅传入字段参与）。body 见 UpdateNotificationTemplateRequest。 */
export const updateNotificationTemplate = (id, body) =>
  request({ method: 'PUT', url: `/api/system/notification-templates/${id}`, data: body })

/** 删除模板（逻辑删除）。 */
export const deleteNotificationTemplate = (id) =>
  request({ method: 'DELETE', url: `/api/system/notification-templates/${id}` })

/** 启停模板。enabled 为布尔。 */
export const toggleNotificationTemplate = (id, enabled) =>
  request({
    method: 'PUT',
    url: `/api/system/notification-templates/${id}/toggle`,
    params: { enabled }
  })
