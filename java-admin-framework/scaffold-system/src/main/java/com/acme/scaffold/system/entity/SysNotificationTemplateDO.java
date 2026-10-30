package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知模板实体（对应 {@code sys_notification_template}，DDL 见 V41__notification_template.sql）。
 *
 * <p>模板中心为 M6 多渠道通知（站内信 / 邮件 / 短信 / Webhook / WebSocket）提供可复用消息样板：
 * 管理员预置标题/正文与 {@code ${var}} 占位变量，发送时按变量映射渲染后再走各 {@code Channel} 适配器。
 * 模板与具体发送解耦，便于统一治理文案、降低重复配置。</p>
 *
 * <p><b>字段约定</b>：{@code channel} 存 {@code Channel} 枚举名大写；{@code enabled} 为 0/1 开关；
 * {@code content} 为 TEXT，存放含 {@code ${var}} 占位的正文（短信渠道忽略 {@code title}）。</p>
 */
@Data
public class SysNotificationTemplateDO {

    private Long id;

    private Long tenantId;

    /** 模板编码（租户内唯一，业务键，形如 ORDER_SHIPPED）。 */
    private String templateCode;

    /** 模板名称（展示用）。 */
    private String templateName;

    /** 渠道：IN_APP / EMAIL / SMS / WEBHOOK / WEBSOCKET。 */
    private String channel;

    /** 标题/主题；短信渠道忽略。 */
    private String title;

    /** 正文，支持 ${var} 占位。 */
    private String content;

    /** 消息类型：NOTICE / ALERT / SYSTEM，默认 NOTICE。 */
    private String msgType;

    /** 是否启用：1 启用 / 0 停用。 */
    private Integer enabled;

    private String remark;

    private Integer version;

    private Integer deleted;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
