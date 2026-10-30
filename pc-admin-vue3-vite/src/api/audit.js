import { request } from './http'

/**
 * GET /api/system/audit-logs/page -> PageResult<AuditLogView>
 *
 * 操作日志分页（V29 操作日志页）。筛选：模块/操作类型/操作人/结果/traceId/关键词/耗时下限/时间区间；
 * 摘要字段按权限脱敏（system:audit:sensitive 可见原文）。
 */
export const pageAuditLogs = (query) =>
  request({ method: 'GET', url: '/api/system/audit-logs/page', params: query })

/**
 * GET /api/system/audit-logs/summary -> AuditLogSummary（总数/成功/失败/慢操作/平均与最大耗时）
 */
export const auditLogSummary = (params) =>
  request({ method: 'GET', url: '/api/system/audit-logs/summary', params })

/**
 * GET /api/system/audit-logs/trace/{traceId} -> AuditLogView[]（一次请求的完整操作链路）
 */
export const auditLogTrace = (traceId) =>
  request({ method: 'GET', url: `/api/system/audit-logs/trace/${encodeURIComponent(traceId)}` })

/**
 * GET /api/system/audit-logs/retention -> AuditRetentionView（留存天数 / 清理截止时间）
 *
 * 留存天数 ≤0 表示禁用自动清理（cutoff 为 null）。前端据此在页头展示「当前留存 N 天」。
 */
export const auditLogRetention = () =>
  request({ method: 'GET', url: '/api/system/audit-logs/retention' })
