import { request } from './http'

// GET /api/system/announcements/page?page&size&keyword&status&onlyValid -> PageResult<AnnouncementView>
export const pageAnnouncements = (query) =>
  request({ method: 'GET', url: '/api/system/announcements/page', params: query })

// GET /api/system/announcements/{id} -> AnnouncementView
export const getAnnouncement = (id) =>
  request({ method: 'GET', url: `/api/system/announcements/${id}` })

// POST /api/system/announcements -> Long
export const createAnnouncement = (body) =>
  request({ method: 'POST', url: '/api/system/announcements', data: body })

// PUT /api/system/announcements/{id} -> void
export const updateAnnouncement = (id, body) =>
  request({ method: 'PUT', url: `/api/system/announcements/${id}`, data: body })

// POST /api/system/announcements/{id}/publish?publishAt&expireAt -> void
export const publishAnnouncement = (id, params) =>
  request({ method: 'POST', url: `/api/system/announcements/${id}/publish`, params })

// POST /api/system/announcements/{id}/offline -> void
export const offlineAnnouncement = (id) =>
  request({ method: 'POST', url: `/api/system/announcements/${id}/offline` })

// POST /api/system/announcements/{id}/toggle-top -> void
export const toggleAnnouncementTop = (id) =>
  request({ method: 'POST', url: `/api/system/announcements/${id}/toggle-top` })

// DELETE /api/system/announcements/{id} -> void
export const deleteAnnouncement = (id) =>
  request({ method: 'DELETE', url: `/api/system/announcements/${id}` })

// POST /api/system/announcements/{id}/read -> void（标记已读，幂等；详情页打开时调用）
export const markAnnouncementRead = (id) =>
  request({ method: 'POST', url: `/api/system/announcements/${id}/read` })

// GET /api/system/announcements/mine/unread?page&size -> PageResult<AnnouncementView>
export const myUnreadAnnouncements = (query) =>
  request({ method: 'GET', url: '/api/system/announcements/mine/unread', params: query })

// GET /api/system/announcements/{id}/read-receipts?page&size&keyword -> PageResult<AnnouncementReceiptView>
export const pageAnnouncementReceipts = (id, query) =>
  request({ method: 'GET', url: `/api/system/announcements/${id}/read-receipts`, params: query })

// GET /api/system/announcements/{id}/read-stats -> AnnouncementReadStatsView
export const getAnnouncementReadStats = (id) =>
  request({ method: 'GET', url: `/api/system/announcements/${id}/read-stats` })

// POST /api/system/announcements/{id}/remind-unread?limit -> { reminded: n }
export const remindAnnouncementUnread = (id, params) =>
  request({ method: 'POST', url: `/api/system/announcements/${id}/remind-unread`, params })
