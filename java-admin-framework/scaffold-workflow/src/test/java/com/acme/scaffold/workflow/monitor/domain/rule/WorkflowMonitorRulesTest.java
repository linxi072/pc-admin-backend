package com.acme.scaffold.workflow.monitor.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 流程监控规则单测：超时/停留时长、催办防骚扰、筛选归一与批量去重。 */
class WorkflowMonitorRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 25, 10, 0);

    @Test
    void overdueShouldRequireConfiguredPastDue() {
        assertTrue(WorkflowMonitorRules.isOverdue(NOW.minusMinutes(1), NOW));
        assertFalse(WorkflowMonitorRules.isOverdue(NOW, NOW), "恰好到期不算超时");
        assertFalse(WorkflowMonitorRules.isOverdue(NOW.plusMinutes(1), NOW));
        assertFalse(WorkflowMonitorRules.isOverdue(null, NOW), "未设期限永不超时");
        assertFalse(WorkflowMonitorRules.isOverdue(NOW.minusMinutes(1), null));
    }

    @Test
    void durationHelpersShouldNeverReturnNegative() {
        assertEquals(5L, WorkflowMonitorRules.overdueMinutes(NOW.minusMinutes(5), NOW));
        assertEquals(0L, WorkflowMonitorRules.overdueMinutes(NOW.plusMinutes(5), NOW));
        assertEquals(0L, WorkflowMonitorRules.overdueMinutes(null, NOW));
        assertEquals(0L, WorkflowMonitorRules.overdueMinutes(NOW.minusMinutes(5), null));

        assertEquals(5L, WorkflowMonitorRules.stayedMinutes(NOW.minusMinutes(5), NOW));
        assertEquals(0L, WorkflowMonitorRules.stayedMinutes(NOW.plusMinutes(5), NOW));
        assertEquals(0L, WorkflowMonitorRules.stayedMinutes(null, NOW));
        assertEquals(0L, WorkflowMonitorRules.stayedMinutes(NOW.minusMinutes(5), null));
    }

    @Test
    void remindShouldRespectMinimumInterval() {
        assertTrue(WorkflowMonitorRules.canRemind(null, NOW, 10), "从未催办直接放行");
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusMinutes(10), NOW, 10), "边界等于间隔即放行");
        assertFalse(WorkflowMonitorRules.canRemind(NOW.minusMinutes(9), NOW, 10));
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusMinutes(9), NOW, 0), "非正间隔关闭限流");
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusMinutes(9), NOW, -1));
        assertTrue(WorkflowMonitorRules.canRemind(NOW.minusMinutes(9), null, 10));
    }

    @Test
    void statusFilterShouldNormalizeWhitelistAndFallBackToAll() {
        assertEquals("RUNNING", WorkflowMonitorRules.normalizeStatus(" running "));
        assertEquals("APPROVED", WorkflowMonitorRules.normalizeStatus("Approved"));
        assertEquals("REJECTED", WorkflowMonitorRules.normalizeStatus("rejected"));
        assertNull(WorkflowMonitorRules.normalizeStatus("ALL"), "ALL 回落为全部");
        assertNull(WorkflowMonitorRules.normalizeStatus(null));
        assertNull(WorkflowMonitorRules.normalizeStatus("  "));
        assertNull(WorkflowMonitorRules.normalizeStatus("PENDING"), "非法值回落为全部，不惊扰轮询");
    }

    @Test
    void keywordShouldTrimAndTruncateInsteadOfReject() {
        assertEquals("abc", WorkflowMonitorRules.normalizeKeyword("  abc  "));
        assertNull(WorkflowMonitorRules.normalizeKeyword(null));
        assertNull(WorkflowMonitorRules.normalizeKeyword("   "));
        String exact = "k".repeat(WorkflowMonitorRules.MAX_KEYWORD_LENGTH);
        assertEquals(exact, WorkflowMonitorRules.normalizeKeyword(exact));
        assertEquals(exact, WorkflowMonitorRules.normalizeKeyword(exact + "extra"));
    }

    @Test
    void distinctIdsShouldIgnoreNullAndDuplicatesPreservingOrder() {
        assertEquals(
                List.of(3L, 1L, 2L),
                WorkflowMonitorRules.distinctIds(Arrays.asList(3L, null, 1L, 3L, 2L)));
        assertTrue(WorkflowMonitorRules.distinctIds(null).isEmpty());
        assertTrue(WorkflowMonitorRules.distinctIds(List.of()).isEmpty());
    }
}
