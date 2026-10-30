import { request, requestRaw } from './http'
import { downloadExport } from './export'
import { saveBlobFromResponse } from '@/utils/download'

// 表单提交管理（M47 跨表单统一提交管理）：后端 base-path /api/system/form-submissions
// 权限：读 system:form-submission:read；删除 system:form-submission:delete（均自动登记 SUPER_ADMIN）

/** 跨表单提交记录分页（按表单编码 / 提交人 / 关键词检索）。
 *  params: { page, size, formCode, submittedBy, keyword, sortField, sortDir } */
export const pageSubmissions = (params = {}) =>
  request({ method: 'GET', url: '/api/system/form-submissions/page', params })

/** 跨表单提交统计（总数 + 按表单聚合）。 */
export const getSubmissionStats = () =>
  request({ method: 'GET', url: '/api/system/form-submissions/stats' })

/** 表单提交记录详情。 */
export const getSubmissionDetail = (id) =>
  request({ method: 'GET', url: `/api/system/form-submissions/${id}` })

/** 删除表单提交记录（逻辑删除）。 */
export const removeSubmission = (id) =>
  request({ method: 'DELETE', url: `/api/system/form-submissions/${id}` })

/** 直接导出当前筛选条件下的提交记录（复用导出中心 form-submissions 模板）。
 *  filters: 与列表一致的 { formCode, submittedBy, keyword, sortField, sortDir }。 */
export const exportSubmissions = async (filters = {}) => {
  const resp = await downloadExport({ code: 'form-submissions', ...filters })
  return saveBlobFromResponse(resp, 'form-submissions', '', '导出失败')
}
