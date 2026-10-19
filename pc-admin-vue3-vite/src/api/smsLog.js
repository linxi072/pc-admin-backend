import { request } from './http'

/**
 * GET /api/system/sms-records/page -> PageResult<SmsRecordView>
 *
 * 短信发送记录分页（V37 短信记录页）。筛选：结果（SUCCESS/FAILED）/ 网关（LOG/HTTP）/
 * 关键字（批次号/操作人/失败原因/traceId）/ 时间区间；时间区间缺省收敛到最近 7 天（服务端口径）。
 * 记录入库前已脱敏（手机号掩码、正文摘要），无「查看原文」入口。
 */
export const pageSmsRecords = (query) =>
  request({ method: 'GET', url: '/api/system/sms-records/page', params: query })

/**
 * GET /api/system/sms-records/summary -> { total, success, failure, failureRate, lastSentAt }
 *
 * 短信发送统计摘要（与 page 相同筛选口径）。
 */
export const smsRecordSummary = (params) =>
  request({ method: 'GET', url: '/api/system/sms-records/summary', params })
