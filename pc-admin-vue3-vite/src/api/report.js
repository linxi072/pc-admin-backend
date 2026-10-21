import { request, requestRaw } from './http'

/**
 * GET /api/report/types -> ReportTypeMeta[]
 *
 * 报表类型元数据（JOB_EXEC/LOGIN/API_CALL 统计视角；USAGE/CATEGORY/STATUS 使用视角的
 * 前端页面已随 V30「报表管理公共统计页」移除，后端类型与聚合逻辑保留未删）。
 */
export const reportTypes = () => request({ method: 'GET', url: '/api/report/types' })

/**
 * GET /api/report/data -> ReportVO
 *
 * 报表数据：统计视角（JOB_EXEC/LOGIN/API_CALL）返回汇总、趋势、分布分桶与明细表。
 * @param {Object} params
 * @param {('JOB_EXEC'|'LOGIN'|'API_CALL')} params.type 报表类型
 * @param {('TODAY'|'LAST_7D'|'LAST_30D'|'CUSTOM')} params.rangeType 时间范围类型（必填）
 * @param {string} [params.start] 自定义范围起始日期（YYYY-MM-DD，仅 CUSTOM）
 * @param {string} [params.end]   自定义范围结束日期（YYYY-MM-DD，仅 CUSTOM）
 * @param {string} [params.keyword] 关键字筛选（JOB_EXEC→任务名 / LOGIN→用户名 / API_CALL→请求路径）
 */
export const reportData = (params) => request({ method: 'GET', url: '/api/report/data', params })

/**
 * GET /api/report/export -> Blob（CSV，UTF-8 BOM）
 *
 * 报表数据导出，返回原始响应（含 content-disposition 文件名），由调用方触发浏览器下载。
 */
export const reportExport = (params) =>
  requestRaw({ method: 'GET', url: '/api/report/export', params, responseType: 'blob' })
