package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告已读回执：记录「某人在何时确认已读某条公告」。
 *
 * <p>懒插入模型：与站内信（发送即全员 eager 投递回执）不同，本表仅在用户
 * 确认已读（markRead）时才落行，行数 ≈ 实际阅读人数；未读名单由
 * 「活跃用户集合 − 本表已读集合」反连接得出（见 AnnouncementReadService）。
 *
 * <p>唯一键 (tenant_id, announcement_id, user_id) 保证同一用户对同一公告
 * 只有一条回执，markRead 重放/并发不会重复计数。
 */
@Data
public class SysAnnouncementReadDO {

    private Long id;

    private Long tenantId = 0L;
    private Long announcementId;
    private Long userId;
    /** 已读回执时间 */
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
