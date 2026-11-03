package com.acme.scaffold.observability.service;

import com.acme.scaffold.observability.dto.TenantMetricView;
import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import com.acme.scaffold.observability.recorder.TenantMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantMetricsService} 单元测试：委托聚合与视图映射。
 */
@DisplayName("TenantMetricsService 单元测试")
class TenantMetricsServiceTest {

    @Test
    @DisplayName("snapshot 委托 TenantMetrics 并映射为视图（无观测时返回空列表）")
    void snapshotDelegatesAndMaps() {
        TenantMetrics metrics = new TenantMetrics(new ObservabilityProperties());
        TenantMetricsService service = new TenantMetricsService(metrics);
        assertTrue(service.snapshot().isEmpty());
        assertTrue(service.isEnabled());

        metrics.export(List.of(new OtlpSpan("t", "s", null, "GET /x", OtlpSpan.Kind.SERVER,
                1_700_000_000_000_000_000L, 1_700_000_000_050_000_000L,
                List.of(), OtlpSpan.StatusCode.OK, null, 42L)));

        List<TenantMetricView> views = service.snapshot();
        assertEquals(1, views.size());
        assertEquals(42L, views.get(0).tenantId());
        assertEquals(1, views.get(0).requestCount());
        assertEquals(50.0, views.get(0).avgDurationMillis(), 0.001);
    }
}
