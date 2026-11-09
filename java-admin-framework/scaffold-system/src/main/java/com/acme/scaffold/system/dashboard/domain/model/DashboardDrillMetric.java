package com.acme.scaffold.system.dashboard.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 运营看板下钻指标。
 *
 * <p>与 {@code SystemStatsPort} 的六条每日趋势序列一一对应（见 {@code DashboardService#trend}）；
 * 下钻时按该指标在指定日期的逐小时分布返回。前端在报表中心趋势线与首页工作台统计卡上点击时，
 * 携带对应指标与日期调用 {@code /api/system/dashboard/drill}。</p>
 */
public enum DashboardDrillMetric {

    NEW_USERS("新增用户"),
    NEW_TENANTS("新增租户"),
    ACTIVE_TENANTS("活跃租户"),
    LOGIN_LOGS("登录日志"),
    OPERATION_LOGS("操作日志"),
    NOTIFICATIONS("通知发送");

    private final String label;

    DashboardDrillMetric(String label) {
        this.label = label;
    }

    /** 序列化码（与 {@link #name()} 一致），供前端作为指标标识。 */
    @JsonValue
    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }
}
