package com.acme.scaffold.cms.category.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 栏目树节点（自引用组装）。children 为空表示叶子栏目。
 */
public class CategoryNode implements Serializable {

    private Long id;

    private String categoryCode;

    private String categoryName;

    private Integer sortNo;

    private String status;

    private List<CategoryNode> children = new ArrayList<>();

    public CategoryNode() {
    }

    public CategoryNode(Long id, String categoryCode, String categoryName, Integer sortNo, String status) {
        this.id = id;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.sortNo = sortNo;
        this.status = status;
    }

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

    public List<CategoryNode> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryNode> children) {
        this.children = children;
    }
}
