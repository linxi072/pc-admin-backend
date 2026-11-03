package com.acme.scaffold.observability.dto;

/**
 * 租户维度聚合视图（R14 租户维度）。对应 {@code TenantMetrics} 的快照，供 APM 大盘按租户下钻。
 *
 * @param tenantId           租户 ID
 * @param requestCount      累计请求数
 * @param errorCount        累计错误数（status=ERROR）
 * @param avgDurationMillis 平均耗时（毫秒，折算便于前端直接展示）
 * @param errorRate         错误率（0~1）
 * @param lastSeenEpochMs   最近一次观测到的 epoch 毫秒
 */
public record TenantMetricView(long tenantId, long requestCount, long errorCount,
                               double avgDurationMillis, double errorRate,
                               long lastSeenEpochMs) {

    /** 由快照记录映射为视图（避免上层直接依赖 recorder 内部类型）。 */
    public static TenantMetricView from(
            com.acme.scaffold.observability.recorder.TenantMetrics.TenantMetricSnapshot s) {
        return new TenantMetricView(s.tenantId(), s.requestCount(), s.errorCount(),
                s.avgDurationMillis(), s.errorRate(), s.lastSeenEpochMs());
    }
}
