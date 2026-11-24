package com.acme.scaffold.cms.article.domain.model;

import java.time.LocalDateTime;
import lombok.Data;

/** 草稿预览令牌（C6）：UUID 令牌 + 过期时间，逻辑删除。 */
@Data
public class CmsPreviewToken {

    private Long id;

    private Long tenantId = 0L;

    private Long articleId;

    private String token;

    private LocalDateTime expiresAt;

    private String createdBy;

    private Integer version = 0;

    private Integer deleted = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
