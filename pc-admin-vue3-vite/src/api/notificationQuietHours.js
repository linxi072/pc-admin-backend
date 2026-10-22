import { request } from './http'

// 免打扰时段（V44，M14）：「个人中心 → 免打扰」自服务页数据源。
// 后端 base-path：/api/notification-quiet-hours（无权限码，仅登录可访问；
// userId 一律由服务端从安全上下文取，接口签名不存在 userId 参数）。
//
// 时间维总闸：先于「通知订阅」（M13 偏好）在 NotificationService.dispatch 生效——
// 处于免打扰窗口内的定向接收者不投递；ALERT 消息可按 alertBreakthrough 穿透。
// 全部定向接收者均被抑制时，系统记一条「已跳过」（SKIPPED）通知记录并静默跳过（不报错）。
// 广播（角色/机构维度）与 WEBHOOK 无个人作息语义，不过滤。
//
// 时间口径：HH:mm，服务器本地时间；半开区间 [start, end)；
// end 小于 start 表达跨午夜窗口（默认 22:00–08:00）；起止相同被服务端拒绝。

/**
 * 获取我的免打扰配置。
 * 返回：{ enabled, startTime, endTime, alertBreakthrough, quietNow }
 *   enabled          是否启用（未配置时为 false）
 *   startTime/endTime  HH:mm（未配置时为默认 22:00 / 08:00）
 *   alertBreakthrough  告警消息是否穿透免打扰
 *   quietNow          当前是否正处于免打扰中（仅展示）
 */
export const getMyQuietHours = () =>
  request({ method: 'GET', url: '/api/notification-quiet-hours/mine' })

/**
 * 更新我的免打扰（全量快照：一组起止时间，无局部更新语义）。
 * body: { enabled, startTime, endTime, alertBreakthrough }
 * 返回更新后的配置视图（与 GET 同口径，无需再次回查）。
 */
export const updateMyQuietHours = (payload) =>
  request({ method: 'PUT', url: '/api/notification-quiet-hours/mine', data: payload })
