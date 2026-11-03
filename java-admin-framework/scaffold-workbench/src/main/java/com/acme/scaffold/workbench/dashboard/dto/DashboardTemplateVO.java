package com.acme.scaffold.workbench.dashboard.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 看板模板视图（列表 / 详情展示）。{@code layoutJson} 原样回传，由前端解析渲染。
 */
public class DashboardTemplateVO implements Serializable {

    private Long id;

    private String templateCode;

    private String templateName;

    private String description;

    private String layoutJson;

    private String status;

    private Long viewCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public DashboardTemplateVO() {
    }

    public DashboardTemplateVO(Long id, String templateCode, String templateName, String description,
                               String layoutJson, String status, Long viewCount,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.templateCode = templateCode;
        this.templateName = templateName;
        this.description = description;
        this.layoutJson = layoutJson;
        this.status = status;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
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
