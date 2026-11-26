import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import { parseFileName, saveBlob, saveBlobFromResponse } from '../download'

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn() }
}))

describe('统一下载工具', () => {
  let anchor

  beforeEach(() => {
    vi.clearAllMocks()
    anchor = { href: '', download: '', click: vi.fn(), remove: vi.fn() }
    vi.stubGlobal('document', {
      createElement: vi.fn(() => anchor),
      body: { appendChild: vi.fn(), removeChild: vi.fn() }
    })
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:mock'),
      revokeObjectURL: vi.fn()
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('parseFileName', () => {
    it('优先 RFC 5987 filename* 并解码中文名', () => {
      const resp = {
        headers: {
          'content-disposition': 'attachment; filename="fallback.zip"; filename*=UTF-8\'\'%E6%8A%A5%E8%A1%A8.csv'
        }
      }
      expect(parseFileName(resp, '报表管理', 'csv')).toBe('报表.csv')
    })

    it('无文件名响应头时回退到传入名与扩展名', () => {
      expect(parseFileName({}, '报表管理', 'csv')).toBe('报表管理.csv')
      expect(parseFileName({ headers: {} }, '', '')).toBe('download')
    })

    it('filename* 编码非法时保留原始串', () => {
      const resp = { headers: { 'content-disposition': "attachment; filename*=UTF-8''%E4%B8%8D%E5%AE%8C%E6%95%B4%" } }
      expect(parseFileName(resp, '报表管理', 'csv')).toBe('%E4%B8%8D%E5%AE%8C%E6%95%B4%')
    })
  })

  describe('saveBlob', () => {
    it('触发临时链接点击后立即释放对象 URL', () => {
      const blob = new Blob(['x'], { type: 'text/csv' })
      saveBlob(blob, 'report.csv')
      expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
      expect(document.createElement).toHaveBeenCalledWith('a')
      expect(anchor.href).toBe('blob:mock')
      expect(anchor.download).toBe('report.csv')
      expect(document.body.appendChild).toHaveBeenCalledWith(anchor)
      expect(anchor.click).toHaveBeenCalledTimes(1)
      expect(document.body.removeChild).toHaveBeenCalledWith(anchor)
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
    })
  })

  describe('saveBlobFromResponse', () => {
    it('响应体为空时提示兜底文案且不创建下载链接', async () => {
      const ok = await saveBlobFromResponse({}, 'codegen-demo', 'zip', '下载失败')
      expect(ok).toBe(false)
      expect(ElMessage.error).toHaveBeenCalledWith('下载失败')
      expect(URL.createObjectURL).not.toHaveBeenCalled()
    })

    it('空 Blob 时提示内容为空', async () => {
      const ok = await saveBlobFromResponse({ data: new Blob([]) }, '报表管理', 'csv', '导出失败')
      expect(ok).toBe(false)
      expect(ElMessage.error).toHaveBeenCalledWith('导出失败：内容为空')
      expect(URL.createObjectURL).not.toHaveBeenCalled()
    })

    it('业务错误 JSON 时提取 message 且不创建下载链接', async () => {
      const resp = {
        data: new Blob(['{"message":"表名不存在"}'], { type: 'application/json' })
      }
      const ok = await saveBlobFromResponse(resp, 'codegen-demo', 'zip', '下载失败')
      expect(ok).toBe(false)
      expect(ElMessage.error).toHaveBeenCalledWith('表名不存在')
      expect(URL.createObjectURL).not.toHaveBeenCalled()
    })

    it('非 JSON 错误体时回退到兜底文案', async () => {
      const resp = { data: new Blob(['boom'], { type: 'application/json' }) }
      const ok = await saveBlobFromResponse(resp, '报表管理', 'csv', '导出失败')
      expect(ok).toBe(false)
      expect(ElMessage.error).toHaveBeenCalledWith('导出失败')
    })

    it('正常文件响应按 content-disposition 文件名触发下载', async () => {
      const blob = new Blob(['a,b\n1,2'], { type: 'text/csv' })
      const resp = {
        data: blob,
        headers: { 'content-disposition': "attachment; filename=\"报表数据.csv\"" }
      }
      const ok = await saveBlobFromResponse(resp, '报表管理', 'csv', '导出失败')
      expect(ok).toBe(true)
      expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
      expect(anchor.download).toBe('报表数据.csv')
      expect(ElMessage.error).not.toHaveBeenCalled()
    })

    it('非 Blob 响应体先包装再下载', async () => {
      const ok = await saveBlobFromResponse({ data: 'raw-text' }, 'plain', 'txt', '下载失败')
      expect(ok).toBe(true)
      const saved = URL.createObjectURL.mock.calls[0][0]
      expect(saved).toBeInstanceOf(Blob)
      expect(await saved.text()).toBe('raw-text')
      expect(anchor.download).toBe('plain.txt')
    })
  })
})
