import { request, requestRaw } from './http'

/**
 * GET /api/report/types -> ReportTypeMeta[]
 *
 * 报表类型元数据（USAGE 使用统计 / CATEGORY 类别统计 / STATUS 状态统计），供前端切换 Tab。
 */
export const reportTypes = () => request({ method: 'GET', url: '/api/report/types' })

/**
 * GET /api/report/data -> ReportVO
 *
 * 报表数据：基于审计日志聚合的访问量 / 活跃用户数 / 成功率 / 类别分布 / 状态分布。
 * @param {Object} params
 * @param {('USAGE'|'CATEGORY'|'STATUS')} [params.type]    报表类型（默认 USAGE）
 * @param {('TODAY'|'LAST_7D'|'LAST_30D'|'CUSTOM')} params.rangeType 时间范围类型（必填）
 * @param {string} [params.start] 自定义范围起始日期（YYYY-MM-DD，仅 CUSTOM）
 * @param {string} [params.end]   自定义范围结束日期（YYYY-MM-DD，仅 CUSTOM）
 * @param {string} [params.module] 类别筛选（module_code，为空表示全部）
 * @param {boolean} [params.success] 状态筛选（true=成功 / false=失败，为空表示全部）
 * @param {('COUNT'|'RATIO'|'NAME')} [params.sortBy] 类别排序字段（默认 COUNT）
 * @param {('ASC'|'DESC')} [params.order] 排序方向（默认 DESC）
 */
export const reportData = (params) => request({ method: 'GET', url: '/api/report/data', params })

/**
 * GET /api/report/export -> Blob（CSV，UTF-8 BOM）
 *
 * 报表数据导出，返回原始响应（含 content-disposition 文件名），由调用方触发浏览器下载。
 */
export const reportExport = (params) =>
  requestRaw({ method: 'GET', url: '/api/report/export', params, responseType: 'blob' })
