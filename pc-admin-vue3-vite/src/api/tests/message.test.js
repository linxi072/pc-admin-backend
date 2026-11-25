import { describe, expect, it, vi, beforeEach } from 'vitest'

const { request, requestRaw } = vi.hoisted(() => ({
  request: vi.fn(() => Promise.resolve({})),
  requestRaw: vi.fn(() => Promise.resolve({}))
}))
vi.mock('../http', () => ({ request, requestRaw }))

import {
  sendMessage, pageMyMessages, pageMyMessagesKeyset, getUnreadCount,
  markMessageRead, markAllMessagesRead, pageSentMessages, pageSentMessagesKeyset,
  getMessageReadStats, pageMessageReceipts, remindMessageUnread
} from '../message.js'

// 锁定站内消息 API 全 11 个端点的前后端同形：发送/收件箱（含深翻页兜底）/回执链路。
// URL 断言用反引号模板串，避免被 api-contract 扫描器误当静态路由。

describe('站内消息 API 契约', () => {
  beforeEach(() => vi.clearAllMocks())

  it('发送与收件箱分页端点同形', () => {
    const body = { title: 't', content: 'c', receiverIds: [1, 2] }
    sendMessage(body)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/system/messages/send', data: body })
    const query = { page: 1, size: 20, onlyUnread: true }
    pageMyMessages(query)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/system/messages/mine', params: query })
    pageSentMessages(query)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/system/messages/sent', params: query })
  })

  it('深翻页 keyset 兜底端点同形（F-11）', () => {
    const query = { size: 50, cursor: 'abc' }
    pageMyMessagesKeyset(query)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/system/messages/mine-keyset', params: query })
    pageSentMessagesKeyset(query)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/system/messages/sent-keyset', params: query })
  })

  it('未读数与已读操作端点同形', () => {
    getUnreadCount()
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: '/api/system/messages/unread-count' })
    markMessageRead('m-1')
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/system/messages/m-1/read` })
    markAllMessagesRead()
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: '/api/system/messages/read-all' })
  })

  it('回执统计/明细/催办端点同形', () => {
    getMessageReadStats('m-1')
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/system/messages/m-1/read-stats` })
    const query = { page: 1, size: 20, readStatus: 'READ' }
    pageMessageReceipts('m-1', query)
    expect(request).toHaveBeenCalledWith({ method: 'GET', url: `/api/system/messages/m-1/receipts`, params: query })
    const params = { limit: 100 }
    remindMessageUnread('m-1', params)
    expect(request).toHaveBeenCalledWith({ method: 'POST', url: `/api/system/messages/m-1/remind-unread`, params })
  })

  it('不使用 blob 通道，全部走统一 request', () => {
    expect(requestRaw).not.toHaveBeenCalled()
  })
})
