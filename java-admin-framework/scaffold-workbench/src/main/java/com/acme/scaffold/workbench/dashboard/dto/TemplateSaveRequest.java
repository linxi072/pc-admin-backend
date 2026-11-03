package com.acme.scaffold.workbench.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 看板模板创建请求。新建模板默认进入 DRAFT 草稿态；{@code layoutJson} 须为合法 JSON 字符串。
 */
public class TemplateSaveRequest implements Serializable {

    @NotBlank
    @Size(max = 64)
    private String templateCode;

    @NotBlank
    @Size(max = 128)
    private String templateName;

    @Size(max = 512)
    private String description;

    /** 看板布局配置（合法 JSON 字符串）。 */
    private String layoutJson;

    private String status = "DRAFT";

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
}
