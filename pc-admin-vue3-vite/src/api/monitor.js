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

// GET /api/system/monitor/caches -> CacheStatView[]（进程内缓存：条目数/命中率/淘汰数/TTL）
export const getCacheStats = () =>
  request({ method: 'GET', url: '/api/system/monitor/caches' })

// POST /api/system/monitor/caches/{name}/clear -> { name, cleared }（清理后下次读取回源数据库）
export const clearCache = (name) =>
  request({ method: 'POST', url: `/api/system/monitor/caches/${encodeURIComponent(name)}/clear` })

// GET /api/system/monitor/datasource -> PoolStatView（连接池：活跃/空闲/等待线程/池配置）
export const getPoolStats = () =>
  request({ method: 'GET', url: '/api/system/monitor/datasource' })
