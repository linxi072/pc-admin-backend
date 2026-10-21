import { request } from './http'

// 告警收件箱管理（GET/DELETE /api/monitoring/alerts/inbox，权限 system:monitor:read）。
// 契约来源：monitor/alert/controller/AlertInboxController.java。
// 与机器向接收端点（POST /api/monitoring/alerts/webhook，令牌鉴权）读写分离；
// 不提供单条删除：收件箱是「最近 N 条」有界窗口，整体清空语义无歧义。

// GET /api/monitoring/alerts/inbox?severity&status&limit -> AlertInboxItemView[]（最新在前）
export const listAlerts = (params) =>
  request({ method: 'GET', url: '/api/monitoring/alerts/inbox', params })

// GET /api/monitoring/alerts/inbox/stats -> AlertInboxStatsView（容量/总数/状态分布/severity 分布）
export const getAlertStats = () =>
  request({ method: 'GET', url: '/api/monitoring/alerts/inbox/stats' })

// DELETE /api/monitoring/alerts/inbox -> Integer（整体丢弃当前窗口，返回清理条数）
export const clearAlerts = () =>
  request({ method: 'DELETE', url: '/api/monitoring/alerts/inbox' })
