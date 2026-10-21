import { request, requestRaw } from './http'

// GET /api/system/files/page -> PageResult<FileView>
// 列表筛选参数（空值一律 undefined，不传空串）：
//   sourceModule 来源功能模块（精确匹配） / uploaderName 上传人（模糊） /
//   originalName 文件名（模糊） / sortField + sortDirection 服务端排序白名单
export const pageFiles = (params) =>
  request({ method: 'GET', url: '/api/system/files/page', params })

// POST /api/system/files/upload（multipart/form-data，字段 file + sourceModule + bizId）
// -> FileView（记录来源模块 / 上传人 / 大小等元数据）
// 使用 FormData 包裹文件；Content-Type 由浏览器自动生成 multipart boundary，不可手动设置。
export const uploadFile = (file, sourceModule, bizId) => {
  const form = new FormData()
  form.append('file', file)
  form.append('sourceModule', sourceModule)
  if (bizId != null && bizId !== '') form.append('bizId', bizId)
  return request({
    method: 'POST',
    url: '/api/system/files/upload',
    data: form,
    headers: { 'Content-Type': undefined }
  })
}

// GET /api/system/files/download/{id} -> 二进制流（原始响应，走 requestRaw blob 通道）
// 返回完整 axios 响应，业务错误时 content-type 为 application/json（需转文本提示），
// 正常才是文件 Blob——调用方须按 CodegenView 范式防御式处理。
export const downloadFile = (id) =>
  requestRaw({ method: 'GET', url: '/api/system/files/download/' + encodeURIComponent(id), responseType: 'blob' })

// DELETE /api/system/files/{id} -> void
export const deleteFile = (id) =>
  request({ method: 'DELETE', url: '/api/system/files/' + encodeURIComponent(id) })
