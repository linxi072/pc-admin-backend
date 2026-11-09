package com.acme.scaffold.system.notifypref.domain;

import lombok.Data;

/**
 * 通知偏好实体（对应 {@code sys_notification_preference}，DDL 见 V43__notification_preference.sql）。
 *
 * <p><b>懒行模型</b>：仅当用户在「个人中心 → 通知订阅」显式设置过某 (渠道 × 消息类型) 组合时才落行；
 * 无行即默认开通，由 {@code sys_user} 的渠道级总开关（{@code notify_site_message / notify_email /
 * notify_mobile}）兜底。恢复默认 = 物理删行，故本表无 {@code deleted} 列。</p>
 *
 * <p><b>唯一键 {@code uk_user_channel_type}</b>：同一用户对同一 (渠道, 消息类型) 至多一行，
 * 写入走「查现存 → 更新或插入」（本仓 jOOQ 动态 DSL 无 upsert API），唯一索引兜底并发。</p>
 */
@Data
public class SysNotificationPreferenceDO {

    private Long id;

    private Long tenantId = 0L;

    /** 偏好所属用户（自服务页只写本人，与 ProfileService 同边界）。 */
    private Long userId;

    /** 渠道：{@code Channel} 枚举名（IN_APP / EMAIL / SMS / WEBSOCKET；WEBHOOK 不可设偏好）。 */
    private String channel;

    /** 消息类型：NOTICE / ALERT / SYSTEM / CC（站内信消息类型域，与 {@code sys_message.msg_type} 同源）。 */
    private String msgType;

    /** 是否接收：1 = 接收，0 = 免打扰（退订）。 */
    private Integer enabled;
}
