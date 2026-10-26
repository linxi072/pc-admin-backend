package com.acme.scaffold.system.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 运营总览聚合服务（M27 报表大屏 / 后端第一刀）。
 *
 * <p>从 {@code SystemStatsPort} 汇聚系统域计数，经 {@code DashboardRules} 归一为不可变快照。
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
}
