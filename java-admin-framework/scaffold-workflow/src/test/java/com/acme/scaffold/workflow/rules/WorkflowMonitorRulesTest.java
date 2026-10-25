package com.acme.scaffold.workflow.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkflowMonitorRules} 离线单测：超时判定、时长换算、催办间隔、筛选归一。
 * 全部为纯函数断言，不依赖 Spring 上下文与数据库。
 */
class WorkflowMonitorRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0, 0);

    @Test
    @DisplayName("isOverdue：已设期限且过点为真；未设期限/未来/恰好到期为假")
    void isOverdue() {
        assertTrue(WorkflowMonitorRules.isOverdue(NOW.minusMinutes(1), NOW));
        assertFalse(WorkflowMonitorRules.isOverdue(NOW.plusMinutes(1), NOW));
        assertFalse(WorkflowMonitorRules.isOverdue(NOW, NOW));
        assertFalse(WorkflowMonitorRules.isOverdue(null, NOW));
        assertFalse(WorkflowMonitorRules.isOverdue(NOW.minusMinutes(1), null));
    }

    @Test
    @DisplayName("overdueMinutes：超时取整分钟；未到期/未设期限回落 0 不返负")
    void overdueMinutes() {
        assertEquals(30L, WorkflowMonitorRules.overdueMinutes(NOW.minusMinutes(30), NOW));
        assertEquals(0L, WorkflowMonitorRules.overdueMinutes(NOW.plusMinutes(30), NOW));
        assertEquals(0L, WorkflowMonitorRules.overdueMinutes(null, NOW));
        // 秒级不足 1 分钟按 0 计（toMinutes 截断），不放大超时严重度
        assertEquals(0L, WorkflowMonitorRules.overdueMinutes(NOW.minusSeconds(30), NOW));
    }

    @Test
    @DisplayName("stayedMinutes：起始为空或晚于当前均回落 0")
    void stayedMinutes() {
        assertEquals(120L, WorkflowMonitorRules.stayedMinutes(NOW.minusMinutes(120), NOW));
        assertEquals(0L, WorkflowMonitorRules.stayedMinutes(null, NOW));
        assertEquals(0L, WorkflowMonitorRules.stayedMinutes(NOW.plusHours(1), NOW));
    }

    @Test
    @DisplayName("canRemind：从未催办放行；等于间隔放行；不足间隔拦截；间隔非正数不限制")
    void canRemind() {
        assertTrue(WorkflowMonitorRules.canRemind(null, NOW, 30));
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusMinutes(30), NOW, 30));
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusMinutes(31), NOW, 30));
        assertFalse(WorkflowMonitorRules.canRemind(NOW.minusMinutes(29), NOW, 30));
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusSeconds(1), NOW, 0));
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusSeconds(1), NOW, -5));
    }

    @Test
    @DisplayName("normalizeStatus：白名单规范大写；ALL/空/非法按全部回落 null")
    void normalizeStatus() {
        assertEquals("RUNNING", WorkflowMonitorRules.normalizeStatus("running"));
        assertEquals("APPROVED", WorkflowMonitorRules.normalizeStatus(" APPROVED "));
        assertEquals("REJECTED", WorkflowMonitorRules.normalizeStatus("rejected"));
        assertNull(WorkflowMonitorRules.normalizeStatus("ALL"));
        assertNull(WorkflowMonitorRules.normalizeStatus(null));
        assertNull(WorkflowMonitorRules.normalizeStatus("  "));
        assertNull(WorkflowMonitorRules.normalizeStatus("FINISHED"));
    }

    @Test
    @DisplayName("normalizeKeyword：去空白；空回落 null；超长截断到 64")
    void normalizeKeyword() {
        assertNull(WorkflowMonitorRules.normalizeKeyword(null));
        assertNull(WorkflowMonitorRules.normalizeKeyword("   "));
        assertEquals("请假", WorkflowMonitorRules.normalizeKeyword("  请假  "));
        String long64 = "K".repeat(64);
        assertEquals(long64, WorkflowMonitorRules.normalizeKeyword(long64));
        assertEquals(64, WorkflowMonitorRules.normalizeKeyword("K".repeat(65)).length());
    }

    @Test
    @DisplayName("distinctIds：去重保序、滤除 null、空入参回落空列表")
    void distinctIds() {
        assertEquals(List.of(), WorkflowMonitorRules.distinctIds(null));
        assertEquals(List.of(), WorkflowMonitorRules.distinctIds(List.of()));
        assertEquals(List.of(3L, 1L, 2L),
                WorkflowMonitorRules.distinctIds(Arrays.asList(3L, null, 3L, 1L, null, 2L)));
    }
}
