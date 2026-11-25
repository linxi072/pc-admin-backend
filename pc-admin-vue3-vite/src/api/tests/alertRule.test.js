import { describe, expect, it, vi, beforeEach } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  listAlertRules, getAlertRule, createAlertRule, updateAlertRule,
  deleteAlertRule, toggleAlertRule, evaluateAlertRules
} from '../alertRule.js'

// 锁定告警规则 API 全 7 个端点同形（CRUD / 启停 / 立即评估）。
// URL 断言与实现保持一致；契约扫描器已排除 src/api/tests/ 夹具目录。

describe('告警规则 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('列表与详情端点同形', () => {
    const params = { enabled: true }
    listAlertRules(params)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/monitoring/alert-rules', params })
    getAlertRule('r-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/monitoring/alert-rules/r-1` })
  })

  it('增删改端点同形', () => {
    const data = { name: 'CPU 高', enabled: true }
    createAlertRule(data)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/monitoring/alert-rules', data })
    updateAlertRule('r-1', data)
    expect(request).toHaveBeenCalledWith({ method: 'PUT', url: `/api/monitoring/alert-rules/r-1`, data })
    deleteAlertRule('r-1')
    expect(request).toHaveBeenCalledWith({ method: 'DELETE', url: `/api/monitoring/alert-rules/r-1` })
  })

  it('启停与立即评估端点同形', () => {
    toggleAlertRule('r-1', false)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/monitoring/alert-rules/r-1/toggle`,
      params: { enabled: false }
    })
    evaluateAlertRules()
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/monitoring/alert-rules/evaluate' })
  })

  it('不使用 blob 通道，全部走统一 request', () => {
    expect(requestRaw).not.toHaveBeenCalled()
  })
})
