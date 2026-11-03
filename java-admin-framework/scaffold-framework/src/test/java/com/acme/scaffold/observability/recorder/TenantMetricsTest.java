package com.acme.scaffold.observability.recorder;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantMetrics} 单元测试：租户维度聚合（计数 / 错误 / 平均时延）、
 * 无租户上下文跳过、上限淘汰、禁用短路。
 */
@DisplayName("TenantMetrics 租户维度聚合测试")
class TenantMetricsTest {

    private static OtlpSpan span(long tenantId, OtlpSpan.StatusCode status, long durationNanos) {
        return new OtlpSpan("t", "s", null, "GET /x", OtlpSpan.Kind.SERVER,
                1_700_000_000_000_000_000L, 1_700_000_000_000_000_000L + durationNanos,
                List.of(), status, null, tenantId);
    }

    @Test
    @DisplayName("按租户聚合：不同租户计数/错误/平均时延独立累计")
    void aggregatesPerTenant() {
        TenantMetrics metrics = new TenantMetrics(new ObservabilityProperties());
        metrics.export(List.of(
                span(1L, OtlpSpan.StatusCode.OK, 100_000_000L),
                span(1L, OtlpSpan.StatusCode.OK, 300_000_000L),
                span(2L, OtlpSpan.StatusCode.ERROR, 500_000_000L)));

        List<TenantMetrics.TenantMetricSnapshot> snap = metrics.snapshot();
        assertEquals(2, snap.size());

        TenantMetrics.TenantMetricSnapshot t1 = snap.stream()
                .filter(s -> s.tenantId() == 1L).findFirst().orElseThrow();
        assertEquals(2, t1.requestCount());
        assertEquals(0, t1.errorCount());
        // 平均 (100+300)/2 = 200ms
        assertEquals(200.0, t1.avgDurationMillis(), 0.001);
        assertEquals(0.0, t1.errorRate(), 0.0001);

        TenantMetrics.TenantMetricSnapshot t2 = snap.stream()
                .filter(s -> s.tenantId() == 2L).findFirst().orElseThrow();
        assertEquals(1, t2.requestCount());
        assertEquals(1, t2.errorCount());
        assertEquals(1.0, t2.errorRate(), 0.0001);
    }

    @Test
    @DisplayName("无租户上下文（tenantId=null）的 span 被跳过，不污染租户视图")
    void skipsNullTenant() {
        TenantMetrics metrics = new TenantMetrics(new ObservabilityProperties());
        // 经已废弃构造器（tenantId=null）构造的 span
        OtlpSpan noTenant = new OtlpSpan("t", "s", null, "GET /x", OtlpSpan.Kind.SERVER,
                1L, 2L, List.of(), OtlpSpan.StatusCode.OK, null);
        metrics.export(List.of(noTenant, span(7L, OtlpSpan.StatusCode.OK, 10_000_000L)));
        assertEquals(1, metrics.snapshot().size());
        assertEquals(7L, metrics.snapshot().get(0).tenantId());
    }

    @Test
    @DisplayName("超出 maxTenants 上限时淘汰最早观测到的租户")
    void evictsWhenOverCapacity() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.getTenantMetrics().setMaxTenants(2);
        TenantMetrics metrics = new TenantMetrics(props);
        metrics.export(List.of(span(1L, OtlpSpan.StatusCode.OK, 1L)));
        metrics.export(List.of(span(2L, OtlpSpan.StatusCode.OK, 1L)));
        metrics.export(List.of(span(3L, OtlpSpan.StatusCode.OK, 1L)));

        List<TenantMetrics.TenantMetricSnapshot> snap = metrics.snapshot();
        assertEquals(2, snap.size());
        assertFalse(snap.stream().anyMatch(s -> s.tenantId() == 1L), "最早写入的租户 1 应被淘汰");
        assertTrue(snap.stream().anyMatch(s -> s.tenantId() == 2L));
        assertTrue(snap.stream().anyMatch(s -> s.tenantId() == 3L));
    }

    @Test
    @DisplayName("enabled=false 时 export 静默短路，snapshot 恒为空")
    void disabledShortCircuits() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.getTenantMetrics().setEnabled(false);
        TenantMetrics metrics = new TenantMetrics(props);
        assertFalse(metrics.isEnabled());
        metrics.export(List.of(span(1L, OtlpSpan.StatusCode.OK, 1L)));
        assertTrue(metrics.snapshot().isEmpty());
    }

    @Test
    @DisplayName("clear 清空全部租户聚合并返回清除前数量")
    void clearReturnsCount() {
        TenantMetrics metrics = new TenantMetrics(new ObservabilityProperties());
        metrics.export(List.of(span(1L, OtlpSpan.StatusCode.OK, 1L), span(2L, OtlpSpan.StatusCode.OK, 1L)));
        assertEquals(2, metrics.clear());
        assertTrue(metrics.snapshot().isEmpty());
    }
}
