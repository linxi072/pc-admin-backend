import { request, requestRaw } from './http'

// GET /api/system/export/templates -> 拆包后直接是 [{code, displayName, fileName}]
// 模板由后端 ExportTask 实现类自动收集，无需前端维护清单。
export const listExportTemplates = () =>
  request({ method: 'GET', url: '/api/system/export/templates' })

// GET /api/system/export/download?code=... -> 原始 blob 响应（走 requestRaw 通道，不经拆包拦截）
// 返回完整 axios 响应：正常为文件 Blob，业务错误时 content-type 为 application/json（需转文本提示）。
// 调用方须按 FileManagementView 范式防御式处理（区分 Blob 与错误 JSON）。
export const downloadExport = (params) =>
  requestRaw({ method: 'GET', url: '/api/system/export/download', params, responseType: 'blob' })

// E5 异步导出：提交任务 -> 轮询状态 -> 下载产物（大文件避免长连接占用）
export const submitExportJob = (params) =>
  request({ method: 'POST', url: '/api/system/export/jobs', params })

export const getExportJob = (jobId) =>
  request({ method: 'GET', url: `/api/system/export/jobs/${jobId}` })

export const downloadExportJob = (jobId) =>
  requestRaw({ method: 'GET', url: `/api/system/export/jobs/${jobId}/download`, responseType: 'blob' })
