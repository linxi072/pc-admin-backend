package com.acme.scaffold.system.dashboard;

import java.util.List;

/**
 * 运营总览纯函数规则（M27）：负责把各数据源的原始计数归一为不可变快照，
 * 并对所有入参做非负钳制（负数/非法值回落 0），保证展示层绝不出现负值或脏数据。
 *
 * <p>无副作用、不依赖 Spring 与数据库，可直接离线单测；供 {@link DashboardService} 复用。</p>
 */
public final class DashboardRules {

    private DashboardRules() {
    }

    /** 非负钳制：负数回落 0。 */
    public static long clampNonNegative(long value) {
        return value < 0 ? 0L : value;
    }

    /**
     * 构建总览快照：对每个计数做非负钳制；{@code generatedAt} 为 0 或负数时回落为当前时刻。
     */
    public static DashboardOverview buildOverview(
            long tenantCount, long userCount, long roleCount, long menuCount,
            long onlineSessionCount, long idempotencyRecordCount, long operationLogCount,
            long loginLogTodayCount, long generatedAt) {
        long ts = (generatedAt > 0) ? generatedAt : System.currentTimeMillis();
        return new DashboardOverview(
                clampNonNegative(tenantCount),
                clampNonNegative(userCount),
                clampNonNegative(roleCount),
                clampNonNegative(menuCount),
                clampNonNegative(onlineSessionCount),
                clampNonNegative(idempotencyRecordCount),
                clampNonNegative(operationLogCount),
                clampNonNegative(loginLogTodayCount),
                ts);
    }

    /** 全零默认快照（无数据时兜底）。 */
    public static DashboardOverview defaultOverview() {
        return buildOverview(0, 0, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
    }

    /** 快照字段展示顺序（前端看板渲染用，保证稳定）。 */
    public static List<String> metricOrder() {
        return List.of("tenantCount", "userCount", "roleCount", "menuCount",
                "onlineSessionCount", "idempotencyRecordCount",
                "operationLogCount", "loginLogTodayCount");
    }
}
