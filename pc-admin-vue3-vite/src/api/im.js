import { request } from './http'

// ==================== 会话（ImConversationController /api/im/conversation） ====================

// POST /create -> Long（创建者自动入会为群主）
// body: { conversationType: 'P2P' | 'GROUP', title, memberIds: Long[] }
export const createConversation = (body) => request({ method: 'POST', url: '/api/im/conversation/create', data: body })

// GET /my -> ConversationView[]
export const listMyConversations = () => request({ method: 'GET', url: '/api/im/conversation/my' })

// GET /{id} -> ConversationView
export const getConversation = (id) => request({ method: 'GET', url: `/api/im/conversation/${id}` })

// POST /{id}/members?userId=（仅群主）
export const addMember = (id, userId) =>
  request({ method: 'POST', url: `/api/im/conversation/${id}/members`, params: { userId } })

// POST /{id}/members/{userId}/remove（群主移除他人 / 本人退群）
export const removeMember = (id, userId) =>
  request({ method: 'POST', url: `/api/im/conversation/${id}/members/${userId}/remove` })

// POST /{id}/quit（本人退出）
export const quitConversation = (id) => request({ method: 'POST', url: `/api/im/conversation/${id}/quit` })

// POST /{id}/delete（仅群主，逻辑删除）
export const deleteConversation = (id) => request({ method: 'POST', url: `/api/im/conversation/${id}/delete` })

// ==================== 消息（ImMessageController /api/im/message） ====================

// POST /send -> Long
// body: { conversationId, content, msgType: 'TEXT'（缺省）, replyToId? }
export const sendMessage = (body) => request({ method: 'POST', url: '/api/im/message/send', data: body })

// GET /conversation/{conversationId}?page&size -> PageResult<MessageView>（时间正序）
export const listConversationMessages = (conversationId, query) =>
  request({ method: 'GET', url: `/api/im/message/conversation/${conversationId}`, params: query })

// GET /{id} -> MessageView
export const getMessage = (id) => request({ method: 'GET', url: `/api/im/message/${id}` })

// POST /{id}/recall（仅本人）
export const recallMessage = (id) => request({ method: 'POST', url: `/api/im/message/${id}/recall` })

// POST /conversation/{conversationId}/read（标记会话已读）
export const markConversationRead = (conversationId) =>
  request({ method: 'POST', url: `/api/im/message/conversation/${conversationId}/read` })

// GET /unread/count -> Long（当前用户未读总数）
export const unreadCount = () => request({ method: 'GET', url: '/api/im/message/unread/count' })
