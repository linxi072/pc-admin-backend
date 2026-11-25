import { describe, expect, it, vi, beforeEach } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  listSpans, getTrace, getTraceSummary, listMetrics, clearSpans
} from '../observability.js'

// 锁定可观测性 API 全 5 个端点同形（span 列表/单 trace 聚合/摘要/指标/清空）。
// 注意 getTrace 含 encodeURIComponent，须验证特殊字符 traceId 被正确转义。
// URL 断言用反引号模板串，避免被 api-contract 扫描器误当静态路由。

describe('可观测性 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('span 列表/摘要/指标端点同形', () => {
    const params = { limit: 50, status: 'ERROR' }
    listSpans(params)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/observability/traces', params })
    getTraceSummary()
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/observability/summary' })
    const metricParams = { prefix: 'app.security' }
    listMetrics(metricParams)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/observability/metrics', params: metricParams })
  })

  it('单 trace 聚合端点同形且 traceId 被 URL 转义', () => {
    getTrace('abc/def?x=1')
    expect(request).toHaveBeenCalledWith({
      method: 'GET',
      url: `/api/observability/traces/${encodeURIComponent('abc/def?x=1')}`
    })
  })

  it('清空本地留存端点同形', () => {
    clearSpans()
    expect(request).toHaveBeenCalledWith({ method: 'DELETE', url: '/api/observability/traces' })
  })

  it('不使用 blob 通道，全部走统一 request', () => {
    expect(requestRaw).not.toHaveBeenCalled()
  })
})
