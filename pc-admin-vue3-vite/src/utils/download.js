import { ElMessage } from 'element-plus'

/**
 * 文件下载工具：统一处理 requestRaw 返回的 Blob（下载 / 导出 / 模板）。
 *
 * <p>此前 FileManagementView、ExportCenterView、ImportCenterView 各写一份
 * 「区分文件 Blob 与业务错误 JSON → createObjectURL → <a download>」逻辑，
 * 分支细节（空内容、content-disposition 解析）不一致。此处收敛为公共实现，
 * 各页面仅保留差异化的失败文案。</p>
 */

/**
 * 从 content-disposition 解析文件名（优先 RFC 5987 的 filename*），失败回退。
 *
 * @param {object} resp axios 原始响应
 * @param {string} fallbackName 回退文件名（不含扩展名）
 * @param {string} ext 回退扩展名（如 'csv' / 'xlsx'），为空则不补
 * @returns {string} 下载时使用的文件名
 */
export function parseFileName(resp, fallbackName = 'download', ext = '') {
  const cd = resp?.headers?.['content-disposition'] || resp?.headers?.contentDisposition || ''
  const m = cd.match(/filename\*=UTF-8''([^;]+)/i) || cd.match(/filename="?([^";]+)"?/i)
  if (m && m[1]) {
    try {
      return decodeURIComponent(m[1])
    } catch (e) {
      return m[1]
    }
  }
  return ext ? `${fallbackName || 'download'}.${ext}` : fallbackName || 'download'
}

/** 触发浏览器下载：临时对象 URL 用后立即释放，避免内存泄漏。 */
export function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * 保存 requestRaw 返回的响应体并触发下载。
 *
 * <p>失败场景（未返回内容 / 空内容 / 业务错误 JSON）已在本函数内提示，返回 false，
 * 调用方据此决定是否继续（如提示成功、刷新列表）。HTTP / 网络错误由调用方
 * catch（http 拦截器已统一提示）。</p>
 *
 * @param {object} resp axios 原始响应
 * @param {string} fallbackName 回退文件名（不含扩展名）
 * @param {string} ext 回退扩展名
 * @param {string} errorTip 业务错误的兜底提示文案（各页面差异化）
 * @returns {Promise<boolean>} 是否成功触发下载
 */
export async function saveBlobFromResponse(resp, fallbackName, ext = '', errorTip = '下载失败') {
  const raw = resp?.data
  if (raw == null) {
    ElMessage.error(errorTip)
    return false
  }
  const blob = raw instanceof Blob ? raw : new Blob([raw])
  if (!blob.size) {
    ElMessage.error(`${errorTip}：内容为空`)
    return false
  }
  // 业务错误会被包装成 JSON 返回（content-type 仍为 application/json），取出 message 提示
  if (blob.type && blob.type.includes('application/json')) {
    let msg = ''
    try {
      msg = JSON.parse(await blob.text())?.message || ''
    } catch (e) {
      msg = ''
    }
    ElMessage.error(msg || errorTip)
    return false
  }
  saveBlob(blob, parseFileName(resp, fallbackName, ext))
  return true
}
