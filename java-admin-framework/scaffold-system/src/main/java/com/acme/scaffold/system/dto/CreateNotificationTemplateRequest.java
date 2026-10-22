package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建通知模板请求。
 *
 * <p>结构校验由 {@link com.acme.scaffold.system.rules.NotificationTemplateRules#validate} 兜底，
 * 此处以 Bean Validation 做入参层面的快速失败（非空与长度上限）。</p>
 */
public record CreateNotificationTemplateRequest(
        @NotBlank @Size(max = 64, message = "{validation.templateCode.size}") String templateCode,
        @NotBlank @Size(max = 100, message = "{validation.templateName.size}") String templateName,
        @NotBlank(message = "{validation.channel.notBlank}") String channel,
        @Size(max = 200, message = "{validation.templateTitle.size}") String title,
        @NotBlank(message = "{validation.templateContent.notBlank}") String content,
        String msgType,
        Boolean enabled,
        @Size(max = 255, message = "{validation.remark.size}") String remark) {
}
