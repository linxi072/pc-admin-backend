package com.acme.scaffold.cms.article.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文章版本快照（C4）：update 前固化主表当前状态，支持版本列表与回滚。
 *
 * <p>快照为不可变历史：回滚只写回主表并新增一个快照，不删除既有版本。</p>
 */
@Data
public class CmsArticleVersion {

    private Long id;

    private Long tenantId = 0L;

    private Long articleId;

    private Integer versionNo;

    private Long categoryId;

    private String title;

    private String summary;

    private String content;

    private String author;

    private String coverUrl;

    private String status;

    private Long viewCount;

    private LocalDateTime publishedAt;

    private LocalDateTime offlineAt;

    /** 变更备注（如 "更新前快照" / "回滚自 vN"）。 */
    private String remark;

    private Integer version = 0;

    private Integer deleted = 0;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
