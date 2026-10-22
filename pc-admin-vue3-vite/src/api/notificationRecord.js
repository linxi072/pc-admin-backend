import { request } from './http'

/**
 * GET /api/system/notification-records/page -> PageResult<NotificationRecordView>
 *
 * 通知发送记录分页（V42 通知记录页）。筛选：渠道（IN_APP/WEBHOOK/WEBSOCKET/EMAIL/SMS）/
 * 结果（SUCCESS/FAILED）/ 关键字（渠道/模板/标题/正文摘要/接收方/失败原因/traceId/操作人）/
 * 时间区间；时间区间缺省收敛到最近 7 天（服务端口径）。
 * 记录入库前已脱敏摘要（标题/正文摘要化、接收方按渠道掩码），无「查看原文」入口。
 * 口径提示：本表覆盖全部五渠道的每次 dispatch 尝试；短信网关对账另见 /api/system/sms-records。
 */
export const pageNotificationRecords = (query) =>
  request({ method: 'GET', url: '/api/system/notification-records/page', params: query })

/**
 * GET /api/system/notification-records/summary -> { total, success, failure, failureRate, lastSentAt }
 *
 * 通知发送统计摘要（与 page 相同筛选口径）。
 */
export const notificationRecordSummary = (params) =>
  request({ method: 'GET', url: '/api/system/notification-records/summary', params })
