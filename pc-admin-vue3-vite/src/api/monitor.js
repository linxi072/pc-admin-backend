import { request } from './http'

// GET /api/system/monitor/metrics -> ServerMetrics（CPU/内存/磁盘/JVM 实时指标）
export const getServerMetrics = () =>
  request({ method: 'GET', url: '/api/system/monitor/metrics' })

// GET /api/system/monitor/online-summary -> { activeTokens, activeSessions, onlineUsers }
export const getOnlineSummary = () =>
  request({ method: 'GET', url: '/api/system/monitor/online-summary' })

// GET /api/system/monitor/online-sessions?keyword&limit -> OnlineSessionView[]
export const listOnlineSessions = (params) =>
  request({ method: 'GET', url: '/api/system/monitor/online-sessions', params })

// GET /api/system/monitor/error-logs?page&size&from&to&module&keyword -> PageResult<SysOperationLogDO>
export const pageErrorLogs = (query) =>
  request({ method: 'GET', url: '/api/system/monitor/error-logs', params: query })

// GET /api/system/monitor/error-summary?from&to -> [{ moduleCode, resultCode, count }]
export const getErrorSummary = (params) =>
  request({ method: 'GET', url: '/api/system/monitor/error-summary', params })

// GET /api/system/monitor/samples?from&to&limit -> SysMonitorSampleDO[]（历史趋势）
export const listMonitorSamples = (params) =>
  request({ method: 'GET', url: '/api/system/monitor/samples', params })

// POST /api/system/monitor/sample -> { inserted }（手动触发一次采样，供刷新按钮使用）
export const triggerMonitorSample = () =>
  request({ method: 'POST', url: '/api/system/monitor/sample' })
