package com.acme.scaffold.monitor.observability;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import com.acme.scaffold.observability.recorder.SpanRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FrameworkObservabilityAdapter} 单元测试：验证其将 {@link NPlusOneSuspectRegistry}
 * 的聚合计数与 {@link SpanRecorder} 的运行态统计映射为 {@link ObservabilitySnapshot}
 * （无 Spring 容器，纯逻辑离线验证）。
 */
@DisplayName("Framework 跨模块可观测适配器测试")
class FrameworkObservabilityAdapterTest {

    private static OtlpSpan span(OtlpSpan.StatusCode status) {
        return new OtlpSpan("t1", "s1", null, "op", OtlpSpan.Kind.SERVER,
                1000L, 2000L, List.of(), status, "");
    }

    private static SpanRecorder enabledRecorder() {
        return new SpanRecorder(new ObservabilityProperties());
    }

    @Test
    @DisplayName("snapshot 反映 registry 累计/留存/最近越限时间，并聚合 trace 维度")
    void snapshotReflectsRegistryState() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        registry.record("fp-1", "select 1", 10);
        registry.record("fp-2", "select 2", 11);
        registry.record("fp-3", "select 3", 12);

        SpanRecorder recorder = enabledRecorder();
        recorder.record(span(OtlpSpan.StatusCode.OK));
        recorder.record(span(OtlpSpan.StatusCode.ERROR));
        recorder.record(span(OtlpSpan.StatusCode.UNSET));

        FrameworkObservabilityAdapter adapter = new FrameworkObservabilityAdapter(registry, recorder);
        ObservabilitySnapshot s = adapter.snapshot();

        assertEquals(3, s.nPlusOneSuspectTotal());
        assertEquals(3, s.nPlusOneSuspectRetained());
        assertTrue(s.lastBreachAtMillis() > 0);
        // 链路追踪维度
        assertTrue(s.traceRecorderEnabled());
        assertEquals(3, s.traceRetained());
        assertEquals(3, s.traceTotal());
        assertEquals(1, s.traceErrorTotal());
    }

    @Test
    @DisplayName("无越限且无 span 时 snapshot 全零（EMPTY 语义，记录器仍启用）")
    void snapshotEmptyWhenNoBreaches() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        SpanRecorder recorder = enabledRecorder();

        FrameworkObservabilityAdapter adapter = new FrameworkObservabilityAdapter(registry, recorder);
        ObservabilitySnapshot s = adapter.snapshot();
        assertEquals(0, s.nPlusOneSuspectTotal());
        assertEquals(0, s.nPlusOneSuspectRetained());
        assertEquals(0, s.lastBreachAtMillis());
        // 记录器启用但未记录任何 span
        assertTrue(s.traceRecorderEnabled());
        assertEquals(0, s.traceRetained());
        assertEquals(0, s.traceTotal());
        assertEquals(0, s.traceErrorTotal());
    }

    @Test
    @DisplayName("容量淘汰后留存=容量，但累计计数保持单调递增")
    void snapshotCountsEvictedButTotalMonotonic() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(2);
        for (int i = 1; i <= 5; i++) {
            registry.record("fp-" + i, "select " + i, 10);
        }

        SpanRecorder recorder = enabledRecorder();
        // 3 条 OK + 2 条 ERROR = 5 条累计
        recorder.record(span(OtlpSpan.StatusCode.OK));
        recorder.record(span(OtlpSpan.StatusCode.ERROR));
        recorder.record(span(OtlpSpan.StatusCode.OK));
        recorder.record(span(OtlpSpan.StatusCode.ERROR));
        recorder.record(span(OtlpSpan.StatusCode.OK));

        FrameworkObservabilityAdapter adapter = new FrameworkObservabilityAdapter(registry, recorder);
        ObservabilitySnapshot s = adapter.snapshot();
        assertEquals(5, s.nPlusOneSuspectTotal());
        assertEquals(2, s.nPlusOneSuspectRetained());
        assertTrue(s.lastBreachAtMillis() > 0);
        // trace 维度：累计 5 条、ERROR 2 条、全部留存（容量 200 远大于 5）
        assertTrue(s.traceRecorderEnabled());
        assertEquals(5, s.traceRetained());
        assertEquals(5, s.traceTotal());
        assertEquals(2, s.traceErrorTotal());
    }
}
