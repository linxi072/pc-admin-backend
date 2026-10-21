import { request } from './http'

// POST /api/system/notifications/send -> { delivered }
// 跨渠道统一发送端点：channel 指定 IN_APP / WEBHOOK / WEBSOCKET / EMAIL / SMS
// body: { channel, title, content, msgType, emails[]|mobiles[]|webhookUrl|receiverIds/roleIds/orgIds }
// 权限：system:message:send（与站内信发送一致）；带 @Idempotent，重放需换 Idempotency-Key
export const sendNotification = (body) =>
  request({ method: 'POST', url: '/api/system/notifications/send', data: body })
