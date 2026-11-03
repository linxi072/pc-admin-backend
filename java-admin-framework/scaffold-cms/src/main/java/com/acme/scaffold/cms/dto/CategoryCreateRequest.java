package com.acme.scaffold.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 栏目创建请求。
 */
public class CategoryCreateRequest implements Serializable {

    @NotBlank
    @Size(max = 64)
    private String categoryCode;

    @NotBlank
    @Size(max = 128)
    private String categoryName;

    @Size(max = 512)
    private String description;

    /** 父栏目 ID；null 或 0 表示根栏目。 */
    private Long parentId;

    private Integer sortNo = 0;

    /** 栏目启用态：ACTIVE / DISABLED，默认 ACTIVE。 */
    private String status = "ACTIVE";

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
