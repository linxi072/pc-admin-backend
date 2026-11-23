import { describe, expect, it, vi } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  listExportTemplates,
  downloadExport,
  submitExportJob,
  getExportJob,
  downloadExportJob
} from '../export.js'

describe('导出 API 契约（E5）', () => {
  it('模板列表与同步下载仍走既有端点（零回归）', () => {
    listExportTemplates()
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/system/export/templates' })
    downloadExport({ code: 'files', format: 'PDF' })
    expect(requestRaw).toHaveBeenCalledWith({
      method: 'GET',
      url: '/api/system/export/download',
      params: { code: 'files', format: 'PDF' },
      responseType: 'blob'
    })
  })

  it('异步任务提交/查询/下载端点路径与后端一致', () => {
    submitExportJob({ code: 'files', format: 'PDF' })
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: '/api/system/export/jobs',
      params: { code: 'files', format: 'PDF' }
    })
    const jobId = 'abc123'
    getExportJob(jobId)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/system/export/jobs/${jobId}` })
    downloadExportJob(jobId)
    expect(requestRaw).toHaveBeenCalledWith({
      method: 'GET',
      url: `/api/system/export/jobs/${jobId}/download`,
      responseType: 'blob'
    })
  })
})
