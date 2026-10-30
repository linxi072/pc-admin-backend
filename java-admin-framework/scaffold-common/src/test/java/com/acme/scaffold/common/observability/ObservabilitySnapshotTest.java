package com.acme.scaffold.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ObservabilitySnapshot} 单元测试：覆盖新扩展的链路追踪维度字段、
 * EMPTY 哨兵语义与 {@link ObservabilitySnapshot#clampNonNegative()} 钳制行为。
 */
@DisplayName("跨模块可观测快照测试")
class ObservabilitySnapshotTest {

    @Test
    @DisplayName("EMPTY 哨兵所有计数为 0 且记录器未启用")
    void emptyHasAllZerosAndRecorderDisabled() {
        ObservabilitySnapshot s = ObservabilitySnapshot.EMPTY;
        assertEquals(0, s.nPlusOneSuspectTotal());
        assertEquals(0, s.nPlusOneSuspectRetained());
        assertEquals(0, s.lastBreachAtMillis());
        assertFalse(s.traceRecorderEnabled());
        assertEquals(0, s.traceRetained());
        assertEquals(0, s.traceTotal());
        assertEquals(0, s.traceErrorTotal());
    }

    @Test
    @DisplayName("带链路追踪字段的快照正确承载各维度计数")
    void snapshotCarriesTraceDimensions() {
        ObservabilitySnapshot s = new ObservabilitySnapshot(
                7, 3, 1_700_000_000_000L, true, 12, 45, 4);
        assertEquals(7, s.nPlusOneSuspectTotal());
        assertEquals(3, s.nPlusOneSuspectRetained());
        assertEquals(1_700_000_000_000L, s.lastBreachAtMillis());
        assertTrue(s.traceRecorderEnabled());
        assertEquals(12, s.traceRetained());
        assertEquals(45, s.traceTotal());
        assertEquals(4, s.traceErrorTotal());
    }

    @Test
    @DisplayName("记录器未启用时 traceRecorderEnabled=false 仍承载计数")
    void disabledRecorderKeepsCountsButFlagFalse() {
        ObservabilitySnapshot s = new ObservabilitySnapshot(
                0, 0, 0, false, 0, 0, 0);
        assertFalse(s.traceRecorderEnabled());
        assertEquals(0, s.traceTotal());
        assertEquals(0, s.traceErrorTotal());
    }

    @Test
    @DisplayName("clampNonNegative 仅把负数计数回落 0，boolean 启用态原样保留")
    void clampNonNegativeClampsLongsKeepsBoolean() {
        ObservabilitySnapshot s = new ObservabilitySnapshot(
                -5, -1, -9, true, -12, -45, -4);
        ObservabilitySnapshot c = s.clampNonNegative();

        assertNotSame(s, c); // 不可变：返回新实例
        assertEquals(0, c.nPlusOneSuspectTotal());
        assertEquals(0, c.nPlusOneSuspectRetained());
        assertEquals(0, c.lastBreachAtMillis());
        assertTrue(c.traceRecorderEnabled()); // boolean 不受钳制影响
        assertEquals(0, c.traceRetained());
        assertEquals(0, c.traceTotal());
        assertEquals(0, c.traceErrorTotal());
    }

    @Test
    @DisplayName("clampNonNegative 对合法值原样保留")
    void clampNonNegativePreservesValidValues() {
        ObservabilitySnapshot s = new ObservabilitySnapshot(
                7, 3, 1_700_000_000_000L, true, 12, 45, 4);
        ObservabilitySnapshot c = s.clampNonNegative();

        assertEquals(7, c.nPlusOneSuspectTotal());
        assertEquals(3, c.nPlusOneSuspectRetained());
        assertEquals(1_700_000_000_000L, c.lastBreachAtMillis());
        assertTrue(c.traceRecorderEnabled());
        assertEquals(12, c.traceRetained());
        assertEquals(45, c.traceTotal());
        assertEquals(4, c.traceErrorTotal());
    }
}
