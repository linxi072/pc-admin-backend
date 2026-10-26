package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营总览纯函数规则（M27）：负责把各数据源的原始计数/趋势点归一为不可变快照，
 * 并对所有入参做非负钳制（负数/非法值回落 0），保证展示层绝不出现负值或脏数据。
 *
 * <p>无副作用、不依赖 Spring 与数据库，可直接离线单测；供 {@link DashboardService} 复用。</p>
 */
public final class DashboardRules {

    /** 趋势回溯天数上界（防止超长区间拖垮统计查询）。 */
    public static final int MAX_TREND_DAYS = 90;

    private DashboardRules() {
    }

    /** 非负钳制：负数回落 0。 */
    public static long clampNonNegative(long value) {
        return value < 0 ? 0L : value;
    }

    /** 钳制趋势天数到 [1, {@link #MAX_TREND_DAYS}]。 */
    public static int clampDays(int days) {
        if (days <= 0) {
            return 1;
        }
        return Math.min(days, MAX_TREND_DAYS);
    }

    /**
     * 构建总览快照：对每个计数做非负钳制；跨模块可观测指标回落 {@link ObservabilitySnapshot#EMPTY}；
     * {@code generatedAt} 为 0 或负数时回落为当前时刻。
     */
    public static DashboardOverview buildOverview(
            long tenantCount, long userCount, long roleCount, long menuCount,
            long onlineSessionCount, long idempotencyRecordCount, long operationLogCount,
            long loginLogTodayCount, long generatedAt) {
        return buildOverview(tenantCount, userCount, roleCount, menuCount,
                onlineSessionCount, idempotencyRecordCount, operationLogCount,
                loginLogTodayCount, ObservabilitySnapshot.EMPTY, generatedAt);
    }

    /**
     * 构建总览快照（含跨模块可观测指标）：系统域计数经非负钳制，
     * {@code obs} 为空时按 {@link ObservabilitySnapshot#EMPTY} 处理并再做一次非负钳制，保证绝不出现负值；
     * {@code generatedAt} 为 0 或负数时回落为当前时刻。
     */
    public static DashboardOverview buildOverview(
            long tenantCount, long userCount, long roleCount, long menuCount,
            long onlineSessionCount, long idempotencyRecordCount, long operationLogCount,
            long loginLogTodayCount, ObservabilitySnapshot obs, long generatedAt) {
        long ts = (generatedAt > 0) ? generatedAt : System.currentTimeMillis();
        ObservabilitySnapshot c = (obs == null) ? ObservabilitySnapshot.EMPTY : obs.clampNonNegative();
        return new DashboardOverview(
                clampNonNegative(tenantCount),
                clampNonNegative(userCount),
                clampNonNegative(roleCount),
                clampNonNegative(menuCount),
                clampNonNegative(onlineSessionCount),
                clampNonNegative(idempotencyRecordCount),
                clampNonNegative(operationLogCount),
                clampNonNegative(loginLogTodayCount),
                c.nPlusOneSuspectTotal(),
                c.nPlusOneSuspectRetained(),
                c.lastBreachAtMillis(),
                ts);
    }

    /** 全零默认快照（无数据时兜底）。 */
    public static DashboardOverview defaultOverview() {
        return buildOverview(0, 0, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
    }

    /**
     * 将稀疏趋势点补齐为连续日期序列：从 {@code today-(days-1)} 到 {@code today} 每日一个点，
     * 缺失日计 0；已有值经非负钳制。用于前端直接按索引渲染，无需再做日期对齐。
     *
     * <p>{@code today} 由调用方注入（生产传 {@code LocalDate.now()}），保证本方法纯函数、可离线单测。</p>
     *
     * @param points 原始（稀疏）趋势点，可含重复日期（取首个）或 null 元素
     * @param days   回溯天数（会被 {@link #clampDays} 钳制）
     * @param today  基准日
     */
    public static List<TrendPoint> fillDailyGaps(List<TrendPoint> points, int days, LocalDate today) {
        int n = clampDays(days);
        LocalDate start = today.minusDays(n - 1L);
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        if (points != null) {
            for (TrendPoint p : points) {
                if (p == null || p.date() == null) {
                    continue;
                }
                map.putIfAbsent(p.date(), clampNonNegative(p.value()));
            }
        }
        List<TrendPoint> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate d = start.plusDays(i);
            out.add(new TrendPoint(d, map.getOrDefault(d, 0L)));
        }
        return out;
    }

    /**
     * 构建趋势快照：对三序列分别补齐连续日期，{@code days} 与 {@code generatedAt} 做归一。
     *
     * @param newUsers      每日新增用户（稀疏，可空）
     * @param newTenants    每日新增租户（稀疏，可空）
     * @param activeTenants 每日活跃租户（稀疏，可空）
     * @param days          回溯天数（会被钳制）
     * @param today         基准日
     * @param generatedAt   生成时刻（epoch 毫秒，<=0 时回落当前时刻）
     */
    public static DashboardTrend buildTrend(
            List<TrendPoint> newUsers, List<TrendPoint> newTenants, List<TrendPoint> activeTenants,
            int days, LocalDate today, long generatedAt) {
        int n = clampDays(days);
        long ts = (generatedAt > 0) ? generatedAt : System.currentTimeMillis();
        return new DashboardTrend(
                n,
                fillDailyGaps(newUsers, n, today),
                fillDailyGaps(newTenants, n, today),
                fillDailyGaps(activeTenants, n, today),
                ts);
    }

    /** 全零趋势兜底（无数据时）。 */
    public static DashboardTrend defaultTrend(int days, LocalDate today) {
        return buildTrend(List.of(), List.of(), List.of(), days, today, System.currentTimeMillis());
    }

    /** 快照字段展示顺序（前端看板渲染用，保证稳定）。 */
    public static List<String> metricOrder() {
        return List.of("tenantCount", "userCount", "roleCount", "menuCount",
                "onlineSessionCount", "idempotencyRecordCount",
                "operationLogCount", "loginLogTodayCount",
                "nPlusOneSuspectTotal", "nPlusOneSuspectRetained", "lastBreachAtMillis");
    }
}
