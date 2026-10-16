import { request, requestRaw } from './http'

// GET /api/system/import/templates -> 拆包后直接是 [{code, displayName, headers}]
// 模板由后端 ImportTask 实现类自动收集，前端无需维护清单。
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
