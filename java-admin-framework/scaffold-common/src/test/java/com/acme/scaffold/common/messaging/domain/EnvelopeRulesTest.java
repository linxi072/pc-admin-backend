package com.acme.scaffold.common.messaging.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnvelopeRules} 信封规则测试（补洞判定 / 重复去重 / 水位推进，纯函数）。
 */
class EnvelopeRulesTest {

    @Test
    void gapAfter_连续无空洞() {
        assertEquals(0L, EnvelopeRules.gapAfter(10L, 11L));
    }

    @Test
    void gapAfter_存在空洞() {
        assertEquals(4L, EnvelopeRules.gapAfter(10L, 15L)); // 11,12,13,14
    }

    @Test
    void gapAfter_重复或滞后为0() {
        assertEquals(0L, EnvelopeRules.gapAfter(10L, 10L));
        assertEquals(0L, EnvelopeRules.gapAfter(10L, 5L));
    }

    @Test
    void isDuplicate_幂等判定() {
        assertTrue(EnvelopeRules.isDuplicate(10L, 10L));
        assertTrue(EnvelopeRules.isDuplicate(10L, 9L));
        assertFalse(EnvelopeRules.isDuplicate(10L, 11L));
    }

    @Test
    void advanceWatermark_单调不减() {
        assertEquals(10L, EnvelopeRules.advanceWatermark(10L, 5L));
        assertEquals(11L, EnvelopeRules.advanceWatermark(10L, 11L));
        assertEquals(0L, EnvelopeRules.advanceWatermark(0L, 0L));
    }
}
