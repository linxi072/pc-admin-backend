import { request, requestRaw } from './http'

// GET /api/system/import/templates -> 拆包后直接是
// [{code, displayName, headers, multiSegment, segments:[{name, headers}]}]
// 模板由后端 ImportTask 实现类自动收集，前端无需维护清单。
// 多段模板（multiSegment=true）仅支持 XLSX 下载（含多工作表），CSV 不可用。
export const listImportTemplates = () =>
  request({ method: 'GET', url: '/api/system/import/templates' })

// GET /api/system/import/template?code=&format= -> 原始 blob 响应（走 requestRaw 通道，不经拆包拦截）
// 返回完整 axios 响应：正常为文件 Blob（默认 XLSX），业务错误时 content-type 为 application/json。
export const downloadImportTemplate = (code, format = 'XLSX') =>
  requestRaw({
    method: 'GET',
    url: '/api/system/import/template',
    params: { code, format },
    responseType: 'blob'
  })

// POST /api/system/import/{code}（multipart）-> 拆包后直接是
// {dryRun,totalRows,successCount,failureCount,errors:[{rowNo,message}]}
//
// 注意：FormData 必须交由 axios 自动设置 multipart boundary，切勿手工指定 Content-Type，
// 否则 boundary 缺失会导致后端无法解析上传内容。
// dryRun=true 时仅预检（不落库），便于用户先确认数据再正式导入。
export const uploadImportFile = (code, file, dryRun = false) => {
  const form = new FormData()
  form.append('file', file)
  return request({
    method: 'POST',
    url: `/api/system/import/${code}`,
    params: { dryRun },
    data: form
  })
}

// POST /api/system/import/{code}?errorReport=true（multipart）-> 原始 blob 响应（走 requestRaw 通道）
// 存在失败行时后端返回 CSV 失败报告附件（text/csv，含原始行值）；
// 无失败行时仍返回 JSON 统计（content-type 为 application/json，Blob 内为 Result 包装，需自行拆 data）。
export const uploadImportFileRaw = (code, file, dryRun = false, errorReport = true) => {
  const form = new FormData()
  form.append('file', file)
  return requestRaw({
    method: 'POST',
    url: `/api/system/import/${code}`,
    params: { dryRun, errorReport },
    data: form,
    responseType: 'blob'
  })
}
