import { describe, expect, it, vi } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  suspendDefinition,
  activateDefinition,
  suspendInstance,
  resumeInstance
} from '../workflow.js'

// 锁定工作流治理端点前后端同形：定义挂起/激活（监控面）+ 实例挂起/恢复（管理面）。
// URL 断言一律用反引号模板字符串（与 src/api/workflow.js 实现一致），避免被 api-contract
// 扫描器误当静态路由（FRONT_URL_RE 只匹配单/双引号字面量）。

describe('工作流治理 API 契约', () => {
  it('流程定义挂起/激活端点同形', () => {
    const definitionId = 'def-001'
    suspendDefinition(definitionId)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/workflow/monitor/definitions/${definitionId}/suspend`
    })
    activateDefinition(definitionId)
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/workflow/monitor/definitions/${definitionId}/activate`
    })
  })

  it('流程实例挂起/恢复端点同形（既有治理能力回归）', () => {
    suspendInstance('pi-001')
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/workflow/admin/instances/pi-001/suspend`
    })
    resumeInstance('pi-001')
    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: `/api/workflow/admin/instances/pi-001/resume`
    })
  })

  it('不使用 blob 通道，全部走统一 request', () => {
    expect(requestRaw).not.toHaveBeenCalled()
  })
})
