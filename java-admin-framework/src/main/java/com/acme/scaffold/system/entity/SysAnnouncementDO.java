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

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
