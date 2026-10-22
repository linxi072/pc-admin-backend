import { request } from './http'

// 通知中心（M9）API：贯穿全站的统一未读入口，聚合「公告未读」与「站内信未读」。
// 接口均为登录用户自身动作（仅登录、不挂权限码），与既有 /api/system/messages/unread-count 同构。
// 所有端点基路径 /api/notification-center（单数），与 M6 复数 /api/system/notifications 无碰撞。

// GET /api/notification-center/summary
// -> { announcementUnread: long, messageUnread: long, totalUnread: long }
export const getSummary = () =>
  request({ method: 'GET', url: '/api/notification-center/summary' })

// GET /api/notification-center/recent?limit= -> List<NotificationItemView>
// item: { source: 'ANNOUNCEMENT'|'MESSAGE', id, title, summary, link, occurredAt }
export const getRecent = (params) =>
  request({ method: 'GET', url: '/api/notification-center/recent', params })

// POST /api/notification-center/mark-all-read -> { announcementMarked, messageMarked, marked }
export const markAllRead = () =>
  request({ method: 'POST', url: '/api/notification-center/mark-all-read' })
