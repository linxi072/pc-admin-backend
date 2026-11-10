package com.acme.scaffold.cms.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 栏目更新请求（按 id 选择性更新，null 字段不覆盖）。
 */
public class CategoryUpdateRequest implements Serializable {

    @NotNull
    private Long id;

    @NotBlank
    @Size(max = 64)
    private String categoryCode;

    @NotBlank
    @Size(max = 128)
    private String categoryName;

    @Size(max = 512)
    private String description;

    private Long parentId;

    private Integer sortNo = 0;

    private String status = "ACTIVE";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
