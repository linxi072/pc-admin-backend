import { request } from './http'

// 可观测性管理（GET /api/observability，权限 system:observability:read / :manage）。
// 契约来源：observability/controller/ObservabilityController.java。

// GET /api/observability/traces?limit&status&traceId -> SpanView[]（最新在前）
export const listSpans = (params) =>
  request({ method: 'GET', url: '/api/observability/traces', params })

// GET /api/observability/traces/{traceId} -> SpanView[]（同一追踪全部 span，时间正序）
export const getTrace = (traceId) =>
  request({ method: 'GET', url: `/api/observability/traces/${encodeURIComponent(traceId)}` })

// GET /api/observability/summary -> TraceSummaryView（记录器/导出器双链路开关与留存统计）
export const getTraceSummary = () =>
  request({ method: 'GET', url: '/api/observability/summary' })

// GET /api/observability/metrics?prefix -> MetricView[]（Micrometer 指标快照）
export const listMetrics = (params) =>
  request({ method: 'GET', url: '/api/observability/metrics', params })

// DELETE /api/observability/traces -> Integer（清空本地 span 留存，不影响 OTLP 导出链路）
export const clearSpans = () =>
  request({ method: 'DELETE', url: '/api/observability/traces' })
