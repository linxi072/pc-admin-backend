import { request } from './http'

// POST /api/system/notifications/send -> { delivered }
// 跨渠道统一发送端点：channel 指定 IN_APP / WEBHOOK / WEBSOCKET / EMAIL / SMS
// body: { channel, title, content, msgType, emails[]|mobiles[]|webhookUrl|receiverIds/roleIds/orgIds }
// 权限：system:message:send（与站内信发送一致）；带 @Idempotent，重放需换 Idempotency-Key
export const sendNotification = (body) =>
  request({ method: 'POST', url: '/api/system/notifications/send', data: body })

// POST /api/system/notifications/send-with-template -> { delivered }
// 按通知模板发送（M11）：templateCode + variables 渲染 ${var} 后投递；渠道/标题/正文取自模板
// body: { templateCode, variables:{}, receiverIds/roleIds/orgIds|emails|mobiles|webhookUrl }
// 权限：system:message:send；带 @Idempotent，重放需换 Idempotency-Key；模板缺失(404)/停用(409)会失败
export const sendWithTemplateNotification = (body) =>
  request({ method: 'POST', url: '/api/system/notifications/send-with-template', data: body })
