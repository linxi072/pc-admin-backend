package com.acme.scaffold.system.dashboard.application;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;
import com.acme.scaffold.common.observability.ObservabilityStatsPort;
import com.acme.scaffold.system.dashboard.domain.model.DashboardDrillMetric;
import com.acme.scaffold.system.dashboard.domain.model.DashboardDrillVO;
import com.acme.scaffold.system.dashboard.domain.model.DashboardOverview;
import com.acme.scaffold.system.dashboard.domain.model.DashboardTrend;
import com.acme.scaffold.system.dashboard.domain.model.HourlyPoint;
import com.acme.scaffold.system.dashboard.domain.repository.SystemStatsPort;
import com.acme.scaffold.system.dashboard.domain.rule.DashboardRules;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 运营总览聚合服务（M27 报表大屏）。
 *
 * <p>从 {@code SystemStatsPort} 汇聚系统域计数/趋势，经 {@code DashboardRules} 归一为不可变快照。
 * 看板为平台级只读视图，由控制器标注 {@code @TenantIgnorable} 实现跨租户全局统计。</p>
 *
 * <p>跨模块可观测指标（如 N+1 疑似）来自 {@code scaffold-framework} 的可选端口实现：
 * 以 {@code @Autowired(required=false)} 注入，未装配（或采集抛异常）时回落 {@link ObservabilitySnapshot#EMPTY}，
 * 不阻断看板，也不引入 system 对 framework 的编译期依赖。</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SystemStatsPort statsPort;
    private ObservabilityStatsPort observabilityPort;

    @Autowired(required = false)
    public void setObservabilityPort(ObservabilityStatsPort observabilityPort) {
        this.observabilityPort = observabilityPort;
    }

    /** 安全获取跨模块可观测快照：无端口或采集异常时回落 {@link ObservabilitySnapshot#EMPTY}。 */
    private ObservabilitySnapshot observabilitySnapshot() {
        if (observabilityPort == null) {
            return ObservabilitySnapshot.EMPTY;
        }
        try {
            ObservabilitySnapshot snapshot = observabilityPort.snapshot();
            return snapshot == null ? ObservabilitySnapshot.EMPTY : snapshot;
        } catch (Exception ignored) {
            // 跨模块指标采集失败不应阻断看板
            return ObservabilitySnapshot.EMPTY;
        }
    }

    public DashboardOverview overview() {
        return DashboardRules.buildOverview(
                statsPort.countTenants(),
                statsPort.countUsers(),
                statsPort.countRoles(),
                statsPort.countMenus(),
                statsPort.countOnlineSessions(),
                statsPort.countIdempotencyRecords(),
                statsPort.countOperationLogs(),
                statsPort.countLoginLogsToday(),
                observabilitySnapshot(),
                System.currentTimeMillis());
    }

    /**
     * 运营趋势（近 days 日时间序列）。
     *
     * @param days 回溯天数；越界值由 {@link DashboardRules#clampDays} 钳制到 [1, 90]
     */
    public DashboardTrend trend(int days) {
        LocalDate today = LocalDate.now();
        return DashboardRules.buildTrend(
                statsPort.dailyNewUsers(days),
                statsPort.dailyNewTenants(days),
                statsPort.dailyActiveTenants(days),
                statsPort.dailyLoginLogs(days),
                statsPort.dailyOperationLogs(days),
                statsPort.dailyNotifications(days),
                days,
                today,
                System.currentTimeMillis());
    }

    /**
     * 运营看板下钻：返回指定指标在指定日期的逐小时分布。
     *
     * <p>最少侵入设计：直接复用 {@code SystemStatsPort#hourlyMetric}（适配器内为新写的逐小时聚合，
     * 与既有每日聚合同源）；缺失日期回落为今日，保证抽屉始终有数据。</p>
     *
     * @param metric 下钻指标（与趋势序列一一对应）
     * @param date   下钻日期（为空时回落为今日）
     */
    public DashboardDrillVO drill(DashboardDrillMetric metric, LocalDate date) {
        LocalDate d = (date == null) ? LocalDate.now() : date;
        List<HourlyPoint> series = statsPort.hourlyMetric(metric, d);
        String isoDate = d.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        return new DashboardDrillVO(metric.name(), isoDate,
                metric.getLabel() + " · " + isoDate + " 逐小时分布", series);
    }
}
