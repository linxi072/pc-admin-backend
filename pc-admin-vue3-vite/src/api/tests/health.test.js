import { beforeEach, describe, expect, it, vi } from 'vitest'

const { rawAxios } = vi.hoisted(() => ({
  rawAxios: { request: vi.fn() }
}))
vi.mock('../http', () => ({ rawAxios }))

import { getLiveness, getReadiness } from '../health.js'

describe('健康探针 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('存活探针走 /livez 且透传任意 HTTP 状态', async () => {
    rawAxios.request.mockResolvedValue({
      status: 200,
      data: { status: 'UP', message: 'ok', details: { jvm: 'UP' } }
    })
    const result = await getLiveness()
    const cfg = rawAxios.request.mock.calls[0][0]

    expect(cfg.method).toBe('GET')
    expect(cfg.url).toBe('/livez')
    expect(cfg.validateStatus(503), '503 是合法 DOWN 业务态，不能被拦截器当错误').toBe(true)
    expect(result).toEqual({
      ok: true,
      httpStatus: 200,
      status: 'UP',
      message: 'ok',
      details: { jvm: 'UP' }
    })
  })

  it('就绪探针走 /readyz 并保留 DOWN 状态与 HTTP 码', async () => {
    rawAxios.request.mockResolvedValue({
      status: 503,
      data: { status: 'DOWN', message: '数据库不可用', details: { db: 'DOWN' } }
    })
    const result = await getReadiness()
    expect(rawAxios.request.mock.calls[0][0].url).toBe('/readyz')
    expect(result).toMatchObject({ ok: true, httpStatus: 503, status: 'DOWN' })
    expect(result.details).toEqual({ db: 'DOWN' })
  })

  it('非探针响应体返回可读格式错误而非 i18n key', async () => {
    rawAxios.request.mockResolvedValue({ status: 200, data: '<html>SPA fallback</html>' })
    const result = await getLiveness()
    expect(result.ok).toBe(false)
    expect(result.httpStatus).toBe(200)
    expect(result.error).toContain('HTTP 200')
    expect(result.error).not.toMatch(/^health\./)
  })

  it('网络失败返回可读诊断并保留原始错误', async () => {
    rawAxios.request.mockRejectedValue(new Error('Network Error'))
    const result = await getReadiness()
    expect(result).toMatchObject({ ok: false, httpStatus: 0, raw: 'Network Error' })
    expect(result.error).toContain('Network Error')
    expect(result.error).not.toMatch(/^health\./)
  })
})
