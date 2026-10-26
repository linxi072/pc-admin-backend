package com.acme.scaffold.system.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 运营总览聚合服务（M27 报表大屏）。
 *
 * <p>从 {@code SystemStatsPort} 汇聚系统域计数/趋势，经 {@code DashboardRules} 归一为不可变快照。
 * 看板为平台级只读视图，由控制器标注 {@code @TenantIgnorable} 实现跨租户全局统计。</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SystemStatsPort statsPort;

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
                days,
                today,
                System.currentTimeMillis());
    }
}

