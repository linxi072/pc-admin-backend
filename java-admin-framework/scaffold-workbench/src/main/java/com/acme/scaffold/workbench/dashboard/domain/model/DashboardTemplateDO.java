package com.acme.scaffold.workbench.dashboard.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 数据看板模板实体（对应 {@code sys_dashboard_template}，DDL 见 V78__dashboard_template.sql，
 * V85 由 {@code dashboard_template} 重命名而来）。
 *
 * <p>{@code layoutJson} 承载看板布局配置（栅格 / 卡片 / 图表等），以 JSON 字符串存储（TEXT 列），
 * 由前端约定结构、后端经 Jackson 校验合法性；{@code status} 表达发布态流转，
 * 由 {@code publishedAt} / {@code offlineAt} 记录态变更时间。</p>
 */
@Data
public class DashboardTemplateDO {

    private Long id;

    private Long tenantId = 0L;

    private String templateCode;

    private String templateName;

    private String description;

    /** 看板布局配置（JSON 字符串）。 */
    private String layoutJson;

    private String status;

    private Long viewCount = 0L;

    private Integer version = 0;

    private Integer deleted = 0;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
