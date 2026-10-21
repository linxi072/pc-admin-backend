package com.acme.scaffold.common.audit;

/**
 * 审计日志统计摘要（用于监控大屏与排障概览）。
 *
 * @param total         区间内总条数
 * @param successCount  成功条数（success=1）
 * @param failureCount  失败条数（success=0）
 * @param slowCount     慢操作条数（duration_ms > {@link #SLOW_THRESHOLD_MS}）
 * @param avgDurationMs 平均耗时（无数据为 0）
 * @param maxDurationMs 最大耗时（无数据为 0）
 */
public record AuditLogSummary(long total, long successCount, long failureCount, long slowCount,
                              long avgDurationMs, long maxDurationMs) {

    /** 慢操作阈值：与监控告警口径一致，超过 1s 视为慢操作。 */
    public static final long SLOW_THRESHOLD_MS = 1000L;

    public AuditLogSummary {
        // 防御：统计口径由聚合查询得出，理论上不会出现负值；出现即说明 SQL 或映射有误，钳到 0 避免脏数据外泄
        avgDurationMs = Math.max(avgDurationMs, 0L);
        maxDurationMs = Math.max(maxDurationMs, 0L);
    }

    public static AuditLogSummary empty() {
        return new AuditLogSummary(0L, 0L, 0L, 0L, 0L, 0L);
    }

    /** 成功率（百分比，保留一位小数）；无数据返回 0.0。 */
    public double successRate() {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((successCount * 10000.0) / total) / 100.0;
    }
}
