package com.acme.scaffold.system.notification.dto;

import com.acme.scaffold.common.notification.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 跨渠道发送请求：在既有站内信字段基础上增加 {@link Channel} 维度。
 * 站内信模式沿用 receiverIds / roleIds / orgIds；Webhook 模式用 webhookUrl 指定目标；
 * WebSocket 模式用 receiverIds 定向（留空广播）；邮件模式用 emails 指定收件人地址。
 */
public record SendNotificationRequest(
        /** 目标渠道：IN_APP（站内信）/ WEBHOOK（Webhook 外发）/ WEBSOCKET（实时推送）/ EMAIL（邮件） */
        Channel channel,
        @NotBlank @Size(max = 200, message = "标题长度不能超过200") String title,
        @NotBlank(message = "消息内容不能为空") String content,
        /** NOTICE(通知) / ALERT(告警) / SYSTEM(系统)，为空默认 NOTICE */
        String msgType,
        /** 单条发送：显式接收人ID列表 */
        List<Long> receiverIds,
        /** 批量发送：按角色筛选 */
        List<Long> roleIds,
        /** 批量发送：按部门筛选 */
        List<Long> orgIds,
        /** Webhook 模式：目标地址（缺省走配置默认地址） */
        String webhookUrl,
        /** 邮件模式：收件人地址列表（非空、单封上限 50，详见 EmailRules） */
        List<String> emails) {
}
