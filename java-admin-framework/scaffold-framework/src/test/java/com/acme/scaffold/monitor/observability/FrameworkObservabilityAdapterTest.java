package com.acme.scaffold.monitor.observability;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FrameworkObservabilityAdapter} 单元测试：验证其将 {@link NPlusOneSuspectRegistry}
 * 的聚合计数映射为 {@link ObservabilitySnapshot}（无 Spring 容器，纯逻辑离线验证）。
 */
@DisplayName("Framework 跨模块可观测适配器测试")
class FrameworkObservabilityAdapterTest {

    @Test
    @DisplayName("snapshot 反映 registry 累计/留存/最近越限时间")
    void snapshotReflectsRegistryState() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        registry.record("fp-1", "select 1", 10);
        registry.record("fp-2", "select 2", 11);
        registry.record("fp-3", "select 3", 12);

        FrameworkObservabilityAdapter adapter = new FrameworkObservabilityAdapter(registry);
        ObservabilitySnapshot s = adapter.snapshot();

        assertEquals(3, s.nPlusOneSuspectTotal());
        assertEquals(3, s.nPlusOneSuspectRetained());
        assertTrue(s.lastBreachAtMillis() > 0);
    }

    @Test
    @DisplayName("无越限时 snapshot 全零（EMPTY 语义）")
    void snapshotEmptyWhenNoBreaches() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        FrameworkObservabilityAdapter adapter = new FrameworkObservabilityAdapter(registry);

        ObservabilitySnapshot s = adapter.snapshot();
        assertEquals(0, s.nPlusOneSuspectTotal());
        assertEquals(0, s.nPlusOneSuspectRetained());
        assertEquals(0, s.lastBreachAtMillis());
    }

    @Test
    @DisplayName("容量淘汰后留存=容量，但累计计数保持单调递增")
    void snapshotCountsEvictedButTotalMonotonic() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(2);
        for (int i = 1; i <= 5; i++) {
            registry.record("fp-" + i, "select " + i, 10);
        }
        FrameworkObservabilityAdapter adapter = new FrameworkObservabilityAdapter(registry);

        ObservabilitySnapshot s = adapter.snapshot();
        assertEquals(5, s.nPlusOneSuspectTotal());
        assertEquals(2, s.nPlusOneSuspectRetained());
        assertTrue(s.lastBreachAtMillis() > 0);
    }
}
