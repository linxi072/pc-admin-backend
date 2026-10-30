import { request } from './http'

// 通知偏好中心（V43，M13）：「个人中心 → 通知订阅」自服务页数据源。
// 后端 base-path：/api/notification-preferences（无权限码，仅登录可访问；
// userId 一律由服务端从安全上下文取，接口签名不存在 userId 参数）。
//
// 两级生效模型：
//   1) 渠道总开关（sys_user.notify_*，在「通知与隐私」Tab 管理）关闭时该渠道一律不投递；
//   2) 细分偏好（本页 4 渠道 × 4 消息类型 = 16 格）：无行默认开通，enabled=0 免打扰。
// 发送链路（NotificationService.dispatch）在路由前按偏好过滤；定向接收者全部退订时
// 记一条 SKIPPED 通知记录并静默跳过（不报错）。

/**
 * 获取我的订阅矩阵。
 * 返回：{ channels: [{ channel, channelEnabled, cells: [{ msgType, enabled, source }] }] }
 * source ∈ CHANNEL（总开关关闭强制关）/ PREFERENCE（显式设置过）/ DEFAULT（无行默认开）。
 */
export const getMyNotificationPreference = () =>
  request({ method: 'GET', url: '/api/notification-preferences/mine' })

/**
 * 批量更新我的订阅（局部更新，仅提交变化的格子）。
 * body: { items: [{ channel, msgType, enabled }] }
 *   enabled = 1 接收 / 0 免打扰 / null 恢复默认（物理删行，回到「无行默认开通」）
 * 返回更新后的完整矩阵（与 GET 同口径，无需再次回查）。
 */
export const updateMyNotificationPreference = (items) =>
  request({ method: 'PUT', url: '/api/notification-preferences/mine', data: { items } })
