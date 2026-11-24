package com.acme.scaffold.system.notifymgmt.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建通知模板请求。
 *
 * <p>结构校验由 {@link com.acme.scaffold.system.notifymgmt.domain.rule.NotificationTemplateRules#validate} 兜底，
 * 此处以 Bean Validation 做入参层面的快速失败（非空与长度上限）。</p>
 */
public record CreateNotificationTemplateRequest(
        @NotBlank @Size(max = 64, message = "模板编码长度不能超过64") String templateCode,
        @NotBlank @Size(max = 100, message = "模板名称长度不能超过100") String templateName,
        @NotBlank(message = "渠道不能为空") String channel,
        @Size(max = 200, message = "标题长度不能超过200") String title,
        @NotBlank(message = "模板内容不能为空") String content,
        String msgType,
        Boolean enabled,
        @Size(max = 255, message = "备注长度不能超过255") String remark) {
}
