package com.acme.scaffold.cms.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 栏目视图。扁平结构，供列表/下拉展示；树形由 {@link CategoryNode} 组装。
 */
public class CategoryView implements Serializable {

    private Long id;

    private Long parentId;

    private String categoryCode;

    private String categoryName;

    private String description;

    private Integer sortNo;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public CategoryView() {
    }

    public CategoryView(Long id, Long parentId, String categoryCode, String categoryName,
                        String description, Integer sortNo, String status,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.parentId = parentId;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.description = description;
        this.sortNo = sortNo;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
