package com.acme.scaffold.system.notifymgmt.domain;

import jakarta.validation.constraints.Size;

/**
 * 更新通知模板请求（全字段可选，体现部分更新语义）。
 *
 * <p>仅传入的非空字段参与更新；未传字段保持原值。字段级结构校验在 Service 层对
 * 「合并后的完整值」统一执行（{@link com.acme.scaffold.system.notifymgmt.domain.rule.NotificationTemplateRules#validate}），
 * 故此处仅做长度上限保护，不做 {@code @NotBlank} 强制。</p>
 */
public record UpdateNotificationTemplateRequest(
        @Size(max = 64, message = "模板编码长度不能超过64") String templateCode,
        @Size(max = 100, message = "模板名称长度不能超过100") String templateName,
        String channel,
        @Size(max = 200, message = "标题长度不能超过200") String title,
        String content,
        String msgType,
        Boolean enabled,
        @Size(max = 255, message = "备注长度不能超过255") String remark) {
}
