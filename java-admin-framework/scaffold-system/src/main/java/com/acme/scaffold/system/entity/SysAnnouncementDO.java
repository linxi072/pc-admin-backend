package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告持久化对象。
 * 状态机：DRAFT(草稿) → PUBLISHED(已发布) → OFFLINE(已下线)。
 */
@Data
public class SysAnnouncementDO {

    private Long id;

    private Long tenantId = 0L;
    private String title;
    private String content;
    /** DRAFT / PUBLISHED / OFFLINE */
    private String status = "DRAFT";
    private Integer isTop = 0;
    private LocalDateTime publishAt;
    private LocalDateTime expireAt;
    private LocalDateTime publishedAt;
    private LocalDateTime offlineAt;
    private Long publisherId;
    private Integer viewCount = 0;
    /** 发布时活跃用户快照（V40 已读率分母；存量公告为 0，统计按 readCount 兜底） */
    private Integer audienceCount = 0;
    /** 已读人数（markRead 成功时 +1，与 sys_announcement_read 回执行一一对应） */
    private Integer readCount = 0;

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
