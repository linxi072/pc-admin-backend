package com.acme.scaffold.workbench.report;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 报表类型：定义「报表管理」可切换的统计视角。
 *
 * <ul>
 *   <li>{@link #USAGE} / {@link #CATEGORY} / {@link #STATUS}：基于审计日志（{@code sys_operation_log}）的使用视角；</li>
 *   <li>{@link #JOB_EXEC}：定时任务执行统计（{@code sys_job_log}），含成功率/失败率/耗时分布/最近明细；</li>
 *   <li>{@link #LOGIN}：系统登录统计（{@code sys_login_log}），含登录次数/活跃用户趋势/异常登录记录；</li>
 *   <li>{@link #API_CALL}：接口调用统计（{@code sys_operation_log}），含调用量/响应时间/错误率。</li>
 * </ul>
 */
public enum ReportType {

    USAGE("使用统计", "按时间维度的访问量、活跃用户数、成功率综合统计"),
    CATEGORY("类别统计", "按功能模块（类别）汇总操作频次与占比"),
    STATUS("状态统计", "按操作成功 / 失败状态汇总分布"),
    JOB_EXEC("定时任务执行统计", "执行成功率、失败率、耗时分布及最近执行明细"),
    LOGIN("登录统计", "登录次数、活跃用户趋势与异常登录记录"),
    API_CALL("接口调用统计", "接口调用量、响应时间分布与错误率");

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
