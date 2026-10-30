package com.acme.scaffold.system.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 按通知模板发送请求（V41 通知模板中心的发送侧，M11）。
 *
 * <p><b>模板是标题/正文/渠道/消息类型的唯一事实来源</b>：服务端按 {@code templateCode}
 * 取「启用中」的模板，用 {@code variables} 渲染 {@code ${var}} 占位后，走既有渠道链路
 * （{@code NotificationService#dispatch}）投递；缺失或停用的模板显式失败，绝不静默降级。</p>
 *
 * <p>请求侧只提供<b>接收人 + 变量取值</b>，不再散落正文，保证文案统一治理。
 * 与原始 {@link SendNotificationRequest}（自带 title/content）<b>并存</b>：是否套用模板由调用方决定，
 * 向后兼容、互不影响。按渠道填充对应接收字段：IN_APP 用 receiverIds/roleIds/orgIds；
 * EMAIL 用 emails；SMS 用 mobiles；WEBSOCKET 用 receiverIds（留空广播）；WEBHOOK 用 webhookUrl。</p>
 *
 * @param templateCode 模板编码（租户内唯一，须为启用中模板）
 * @param variables    变量名 → 取值；缺省为空（未提供的变量渲染后保持 {@code ${name}} 原样）
 */
public record SendWithTemplateNotificationRequest(
        @NotBlank(message = "{validation.templateCode.notBlank}")
        @Size(max = 64, message = "{validation.templateCode.size}") String templateCode,
        Map<String, String> variables,
        List<Long> receiverIds,
        List<Long> roleIds,
        List<Long> orgIds,
        String webhookUrl,
        List<String> emails,
        List<String> mobiles) {
}
