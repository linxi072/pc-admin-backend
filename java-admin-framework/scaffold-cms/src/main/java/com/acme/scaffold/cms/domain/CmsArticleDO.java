package com.acme.scaffold.cms.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容文章实体（对应 {@code cms_article}，DDL 见 V74__cms.sql）。
 *
 * <p>{@code content} 承载富文本（HTML/Markdown 由前端约定）；{@code status} 表达发布态流转，
 * 由 {@code publishedAt} / {@code offlineAt} 记录态变更时间。</p>
 */
@Data
public class CmsArticleDO {

    private Long id;

    private Long tenantId = 0L;

    private Long categoryId;

    private String title;

    private String summary;

    private String content;

    private String author;

    private String coverUrl;

    private String status;

    private Long viewCount = 0L;

    private LocalDateTime publishedAt;

    private LocalDateTime offlineAt;

    private Integer version = 0;

    private Integer deleted = 0;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
