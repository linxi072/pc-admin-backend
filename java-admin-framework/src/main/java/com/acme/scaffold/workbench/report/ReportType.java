package com.acme.scaffold.workbench.report;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表类型：定义「报表管理」可切换的统计视角。
 */
public enum ReportType {

    USAGE("使用统计", "按时间维度的访问量、活跃用户数、成功率综合统计"),
    CATEGORY("类别统计", "按功能模块（类别）汇总操作频次与占比"),
    STATUS("状态统计", "按操作成功 / 失败状态汇总分布");

    private final String label;
    private final String description;

    ReportType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** 序列化码（与 {@link #name()} 一致），供前端作为类型标识。 */
    @JsonValue
    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
