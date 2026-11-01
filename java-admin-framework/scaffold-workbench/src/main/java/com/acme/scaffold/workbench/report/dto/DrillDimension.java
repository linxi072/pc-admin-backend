package com.acme.scaffold.workbench.report.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表下钻维度。
 *
 * <p>前端在既有聚合条件（类型 / 时间范围 / 类别 / 状态 / 关键字）之上叠加一个父级维度过滤，
 * 由后端返回更细粒度的时间序列或明细。各维度适用的报表类型见 {@code ReportService#drill} 的适用性约束：
 * <ul>
 *   <li>{@link #TIME_DAY}：点击趋势天桶，下钻该日逐小时分布（所有报表类型适用）；</li>
 *   <li>{@link #MODULE} / {@link #STATUS} / {@link #REQUEST_PATH}：基于 {@code sys_operation_log} 的视角
 *       （USAGE / CATEGORY / STATUS / API_CALL）按其字段下钻；</li>
 *   <li>{@link #LOGIN_TYPE}：基于 {@code sys_login_log} 的登录视角（LOGIN）按其字段下钻；</li>
 *   <li>{@link #DURATION}：定时任务视角（JOB_EXEC）按耗时分桶返回明细（其余类型退化为整窗趋势）。</li>
 * </ul>
 */
public enum DrillDimension {

    TIME_DAY("按日下钻逐小时"),
    MODULE("按模块下钻"),
    STATUS("按状态下钻"),
    LOGIN_TYPE("按登录方式下钻"),
    REQUEST_PATH("按接口路径下钻"),
    DURATION("按耗时桶下钻明细");

    private final String label;

    DrillDimension(String label) {
        this.label = label;
    }

    /** 序列化码（与 {@link #name()} 一致），供前端作为维度标识。 */
    @JsonValue
    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }
}
