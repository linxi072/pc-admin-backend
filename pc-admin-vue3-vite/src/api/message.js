import { request } from './http'

// POST /api/system/messages/send -> { receiverCount }
// body: { title, content, msgType, receiverIds[], roleIds[], orgIds[] }
export const sendMessage = (body) =>
  request({ method: 'POST', url: '/api/system/messages/send', data: body })

// GET /api/system/messages/mine?page&size&onlyUnread -> PageResult<MessageView>
export const pageMyMessages = (query) =>
  request({ method: 'GET', url: '/api/system/messages/mine', params: query })

// GET /api/system/messages/unread-count -> { unread }
export const getUnreadCount = () =>
  request({ method: 'GET', url: '/api/system/messages/unread-count' })

// POST /api/system/messages/{messageId}/read -> void（幂等，记录回执时间）
export const markMessageRead = (messageId) =>
  request({ method: 'POST', url: `/api/system/messages/${messageId}/read` })

// POST /api/system/messages/read-all -> { updated }
export const markAllMessagesRead = () =>
  request({ method: 'POST', url: '/api/system/messages/read-all' })

// GET /api/system/messages/sent?page&size -> PageResult<SysMessageDO>
export const pageSentMessages = (query) =>
  request({ method: 'GET', url: '/api/system/messages/sent', params: query })
