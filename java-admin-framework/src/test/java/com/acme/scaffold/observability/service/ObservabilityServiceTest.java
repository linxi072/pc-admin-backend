package com.acme.scaffold.observability.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.observability.dto.MetricView;
import com.acme.scaffold.observability.dto.SpanView;
import com.acme.scaffold.observability.dto.TraceSummaryView;
import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import com.acme.scaffold.observability.export.SpanExporter;
import com.acme.scaffold.observability.metrics.MetricsRegistry;
import com.acme.scaffold.observability.recorder.SpanRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ObservabilityService} 单元测试：输入校验（limit 越界 / status 非法 / traceId 空）、
 * 过滤映射、摘要聚合、指标快照前缀过滤。span 记录器使用真实内存组件，导出器与指标注册表以 Mockito 桩接。
 */
@DisplayName("ObservabilityService 单元测试")
class ObservabilityServiceTest {

    private SpanRecorder recorder;
    private SpanExporter exporter;
    private MetricsRegistry metricsRegistry;
    private ObservabilityService service;

    @BeforeEach
    void setUp() {
        recorder = new SpanRecorder(new ObservabilityProperties());
        exporter = mock(SpanExporter.class);
        metricsRegistry = new MetricsRegistry(new SimpleMeterRegistry());
        service = new ObservabilityService(recorder, exporter, metricsRegistry);
    }

    private static OtlpSpan span(String traceId, OtlpSpan.StatusCode status) {
        return new OtlpSpan(traceId, "s-" + traceId, null, "GET /api/users",
                OtlpSpan.Kind.SERVER, 1_700_000_000_000_000_000L, 1_700_000_000_050_000_000L,
                List.of(new OtlpSpan.Attribute("http.status_code", "200")), status, null);
    }

    @Test
    @DisplayName("recentSpans：limit 为空取缺省 50，status/traceId 过滤后映射为视图")
    void recentSpans_defaultLimitAndFilters() {
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t1", OtlpSpan.StatusCode.ERROR));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));

        List<SpanView> all = service.recentSpans(null, null, null);
        assertEquals(3, all.size());

        List<SpanView> errors = service.recentSpans(50, "error", null);
        assertEquals(1, errors.size());
        assertEquals(OtlpSpan.StatusCode.ERROR.name(), errors.get(0).status());
        assertEquals(50L, errors.get(0).durationMillis());
        assertEquals("200", errors.get(0).attributes().get("http.status_code"));
        assertEquals("GET /api/users", errors.get(0).name());

        List<SpanView> byTrace = service.recentSpans(50, null, " t1 ");
        assertEquals(2, byTrace.size());
        assertTrue(byTrace.stream().allMatch(v -> v.traceId().equals("t1")));
    }

    @Test
    @DisplayName("recentSpans：limit=0 与超容量均抛 VALIDATION_ERROR")
    void recentSpans_limitOutOfRange() {
        assertThrows(BusinessException.class, () -> service.recentSpans(0, null, null));
        assertThrows(BusinessException.class, () -> service.recentSpans(recorder.capacity() + 1, null, null));
    }

    @Test
    @DisplayName("recentSpans：status 非枚举值抛 VALIDATION_ERROR")
    void recentSpans_illegalStatus() {
        assertThrows(BusinessException.class, () -> service.recentSpans(10, "WEIRD", null));
    }

    @Test
    @DisplayName("spansByTraceId：空 traceId 抛 VALIDATION_ERROR；正常时按时间正序映射")
    void spansByTraceId_validationAndMapping() {
        assertThrows(BusinessException.class, () -> service.spansByTraceId("  "));
        assertThrows(BusinessException.class, () -> service.spansByTraceId(null));

        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));
        recorder.record(span("t1", OtlpSpan.StatusCode.ERROR));

        List<SpanView> views = service.spansByTraceId("t1");
        assertEquals(2, views.size());
        assertEquals("OK", views.get(0).status());
        assertEquals("ERROR", views.get(1).status());
        assertTrue(service.spansByTraceId("missing").isEmpty());
    }

    @Test
    @DisplayName("clearSpans 委托记录器并返回清除条数")
    void clearSpans_delegates() {
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));
        assertEquals(2, service.clearSpans());
        assertEquals(0, recorder.size());
    }

    @Test
    @DisplayName("summary 聚合记录器统计与导出器开关")
    void summary_aggregatesBothChannels() {
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.ERROR));
        when(exporter.isEnabled()).thenReturn(true);

        TraceSummaryView view = service.summary();
        assertTrue(view.recorderEnabled());
        assertTrue(view.exporterEnabled());
        assertEquals(2, view.retained());
        assertEquals(1L, view.okCount());
        assertEquals(1L, view.errorCount());
        assertEquals(0L, view.unsetCount());
        assertEquals(recorder.capacity(), view.capacity());
    }

    @Test
    @DisplayName("metricSnapshot：按前缀过滤并按名称排序，值取自 meter 测量")
    void metricSnapshot_prefixFilterAndOrdering() {
        metricsRegistry.counter("app.observability.test.spans", "span 总数").increment();
        metricsRegistry.counter("app.other.metric", "其它指标").increment(3);

        List<MetricView> all = service.metricSnapshot(null);
        List<String> names = all.stream().map(MetricView::name).toList();
        assertEquals(names.stream().sorted().toList(), names);
        assertTrue(names.contains("app.observability.test.spans"));

        List<MetricView> filtered = service.metricSnapshot("app.observability.");
        assertEquals(1, filtered.size());
        MetricView view = filtered.get(0);
        assertEquals("app.observability.test.spans", view.name());
        assertEquals("COUNTER", view.type());
        assertEquals("span 总数", view.description());
        assertNotNull(view.value());
        assertEquals(1.0, view.value());
        assertEquals(Map.of(), view.tags());
    }

    @Test
    @DisplayName("metricSnapshot：空前缀按全量处理")
    void metricSnapshot_blankPrefixMeansAll() {
        metricsRegistry.counter("app.x", "x").increment();
        assertFalse(service.metricSnapshot("  ").isEmpty());
        verify(exporter, never()).isEnabled();
    }
}
