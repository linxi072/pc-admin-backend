import { describe, expect, it, vi } from 'vitest'
import { settlePageWithSummary } from '../auditLogSummary'

describe('操作日志摘要复用编排', () => {
  it('正常路径并行结算分页与摘要', async () => {
    const page = Promise.resolve({ records: [{ id: 1 }], total: 1 })
    const fetchSummary = vi.fn(async () => ({ success: 1, failed: 0 }))

    const { pageResult, summaryResult } = await settlePageWithSummary(page, fetchSummary)

    expect(fetchSummary).toHaveBeenCalledTimes(1)
    expect(pageResult.status).toBe('fulfilled')
    expect(pageResult.value.records).toHaveLength(1)
    expect(summaryResult.status).toBe('fulfilled')
    expect(summaryResult.value).toEqual({ success: 1, failed: 0 })
  })

  it('游标降级复用已成功的摘要且不再发起请求', async () => {
    const prefetched = { status: 'fulfilled', value: { success: 2, failed: 1 } }
    const fetchSummary = vi.fn(async () => ({ success: 99, failed: 99 }))

    const { summaryResult } = await settlePageWithSummary(
      Promise.resolve({ records: [], nextCursor: 'next' }),
      fetchSummary,
      prefetched
    )

    expect(fetchSummary).not.toHaveBeenCalled()
    expect(summaryResult).toBe(prefetched)
    expect(summaryResult.value).toEqual({ success: 2, failed: 1 })
  })

  it('预取摘要失败时仍重试一次并采用新结果', async () => {
    const prefetched = { status: 'rejected', reason: new Error('首次摘要失败') }
    const fetchSummary = vi.fn(async () => ({ success: 3, failed: 0 }))

    const { summaryResult } = await settlePageWithSummary(
      Promise.resolve({ records: [] }),
      fetchSummary,
      prefetched
    )

    expect(fetchSummary).toHaveBeenCalledTimes(1)
    expect(summaryResult.status).toBe('fulfilled')
    expect(summaryResult.value).toEqual({ success: 3, failed: 0 })
  })

  it('分页失败不阻断摘要结算', async () => {
    const pageError = new Error('offset too large')
    const fetchSummary = vi.fn(async () => ({ success: 4, failed: 0 }))

    const { pageResult, summaryResult } = await settlePageWithSummary(
      Promise.reject(pageError),
      fetchSummary
    )

    expect(pageResult.status).toBe('rejected')
    expect(pageResult.reason).toBe(pageError)
    expect(fetchSummary).toHaveBeenCalledTimes(1)
    expect(summaryResult.status).toBe('fulfilled')
  })
})
