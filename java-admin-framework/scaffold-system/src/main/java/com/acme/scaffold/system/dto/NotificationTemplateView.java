package com.acme.scaffold.system.dto;

import com.acme.scaffold.system.entity.SysNotificationTemplateDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知模板视图（对应 {@code sys_notification_template}）。
 *
 * <p>{@code variableNames} 由正文中的 {@code ${var}} 占位实时解析得到（无独立存储列），
 * 供前端渲染「变量输入表单」；{@code enabled} 由整型 0/1 映射为布尔。</p>
 *
 * @param id              模板 ID
 * @param templateCode    模板编码（租户内唯一）
 * @param templateName    模板名称
 * @param channel         渠道（Channel 枚举大写）
 * @param title           标题/主题（短信忽略）
 * @param content         正文（含 ${var} 占位）
 * @param msgType         消息类型：NOTICE / ALERT / SYSTEM
 * @param enabled         是否启用
 * @param variableNames   正文中提取的变量名列表（保序去重）
 * @param remark          备注
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record NotificationTemplateView(
        Long id,
        String templateCode,
        String templateName,
        String channel,
        String title,
        String content,
        String msgType,
        boolean enabled,
        List<String> variableNames,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 由持久化对象 + 预解析变量名构造视图（null 返回 null）。 */
    public static NotificationTemplateView of(SysNotificationTemplateDO d, List<String> variableNames) {
        if (d == null) {
            return null;
        }
        return new NotificationTemplateView(
                d.getId(), d.getTemplateCode(), d.getTemplateName(), d.getChannel(),
                d.getTitle(), d.getContent(), d.getMsgType(),
                d.getEnabled() != null && d.getEnabled() == 1,
                variableNames, d.getRemark(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
