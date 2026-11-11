package com.acme.scaffold.cms.category.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 内容栏目实体（对应 {@code cms_category}，DDL 见 V74__cms.sql）。
 *
 * <p>树形结构通过 {@code parentId} 自引用表达；{@code parentId} 为 null 表示根栏目。</p>
 */
@Data
public class CmsCategoryDO {

    private Long id;

    private Long tenantId = 0L;

    private Long parentId;

    private String categoryCode;

    private String categoryName;

    private String description;

    private Integer sortNo = 0;

    private String status;

    private Integer version = 0;

    private Integer deleted = 0;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
