package com.acme.scaffold.workflow.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkflowInstanceAdminRules} 单测：挂起/恢复转移 + 治理审计 operationId 构造。
 *
 * <p>operationId 回归背景：{@code reassign} 会在同一毫秒内为多个活跃任务各插一条审计，
 * 原实现仅用毫秒时间戳作后缀，直接撞 {@code uk_operation_id} 令整个事务回滚。</p>
 */
class WorkflowInstanceAdminRulesTest {

    @Test
    @DisplayName("挂起/恢复转移判定保持原语义")
    void transitions() {
        assertEquals(WorkflowInstanceAdminRules.InstanceStateTransition.ALREADY_SUSPENDED,
                WorkflowInstanceAdminRules.evaluateSuspend(true));
        assertEquals(WorkflowInstanceAdminRules.InstanceStateTransition.OK,
                WorkflowInstanceAdminRules.evaluateSuspend(false));
        assertEquals(WorkflowInstanceAdminRules.InstanceStateTransition.OK,
                WorkflowInstanceAdminRules.evaluateResume(true));
        assertEquals(WorkflowInstanceAdminRules.InstanceStateTransition.NOT_SUSPENDED,
                WorkflowInstanceAdminRules.evaluateResume(false));
    }

    @Test
    @DisplayName("operationId：保留可读前缀，不同唯一后缀必然不同")
    void operationIdKeepsReadablePrefixAndIsUnique() {
        String a = WorkflowInstanceAdminRules.operationId("REASSIGN", "pi-1", "1700000000000-1-abcd1234");
        String b = WorkflowInstanceAdminRules.operationId("REASSIGN", "pi-1", "1700000000000-2-efgh5678");
        assertTrue(a.startsWith("reassign-pi-1-"), a);
        assertNotEquals(a, b, "同一毫秒内多条审计必须有不同的 operationId");
        assertEquals(64, WorkflowInstanceAdminRules.OPERATION_ID_MAX_LENGTH);
    }

    @Test
    @DisplayName("operationId：超长实例 ID 与后缀下仍不越过 VARCHAR(64)")
    void operationIdNeverExceedsColumnWidth() {
        String longPi = "p".repeat(200);
        String longSuffix = "s".repeat(200);
        String id = WorkflowInstanceAdminRules.operationId("MIGRATE", longPi, longSuffix);
        assertTrue(id.length() <= WorkflowInstanceAdminRules.OPERATION_ID_MAX_LENGTH,
                "operationId 长度=" + id.length());
        assertFalse(id.isBlank());
    }

    @Test
    @DisplayName("operationId：null/空入参安全，动作统一小写")
    void operationIdHandlesNullInputs() {
        String id = WorkflowInstanceAdminRules.operationId(null, null, null);
        assertTrue(id.startsWith("op--"), id);
        assertTrue(id.length() <= WorkflowInstanceAdminRules.OPERATION_ID_MAX_LENGTH);
        assertTrue(WorkflowInstanceAdminRules.operationId("SUSPEND", "pi-9", "x").startsWith("suspend-"));
    }
}
