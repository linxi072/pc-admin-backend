package com.acme.scaffold.workflow.admin.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.workflow.admin.domain.rule.WorkflowInstanceAdminRules.InstanceStateTransition;
import org.junit.jupiter.api.Test;

/** 流程实例治理规则单测：挂起/恢复转移判定与审计 operationId 唯一性/列宽约束。 */
class WorkflowInstanceAdminRulesTest {

    @Test
    void suspendTransitionTruthTable() {
        assertEquals(InstanceStateTransition.OK, WorkflowInstanceAdminRules.evaluateSuspend(false));
        assertEquals(InstanceStateTransition.ALREADY_SUSPENDED, WorkflowInstanceAdminRules.evaluateSuspend(true));
    }

    @Test
    void resumeTransitionTruthTable() {
        assertEquals(InstanceStateTransition.OK, WorkflowInstanceAdminRules.evaluateResume(true));
        assertEquals(InstanceStateTransition.NOT_SUSPENDED, WorkflowInstanceAdminRules.evaluateResume(false));
    }

    @Test
    void operationIdShouldKeepReadableLowercasePrefixAndUniqueSuffix() {
        assertEquals(
                "reassign-123-abc-1",
                WorkflowInstanceAdminRules.operationId("REASSIGN", "123", "abc-1"));
    }

    @Test
    void operationIdShouldFallbackForBlankActionAndNullIds() {
        assertEquals("op--", WorkflowInstanceAdminRules.operationId(null, null, null));
        assertEquals("op--u1", WorkflowInstanceAdminRules.operationId("  ", null, "u1"));
    }

    @Test
    void operationIdShouldDifferentiateByUniqueSuffix() {
        String first = WorkflowInstanceAdminRules.operationId("REASSIGN", "123", "1-a");
        String second = WorkflowInstanceAdminRules.operationId("REASSIGN", "123", "2-b");
        assertNotEquals(first, second, "同实例多条审计不得撞 uk_operation_id");
    }

    @Test
    void operationIdShouldTruncateLongPrefixWithinColumnWidth() {
        String id = WorkflowInstanceAdminRules.operationId("A".repeat(100), "1234567890", "x");
        assertEquals(WorkflowInstanceAdminRules.OPERATION_ID_MAX_LENGTH, id.length());
        assertTrue(id.endsWith("-x"), "后缀唯一性段必须保留");
    }

    @Test
    void operationIdShouldKeepSuffixTailWhenSuffixOverBudget() {
        String id = WorkflowInstanceAdminRules.operationId("MIGRATE", "123", "S".repeat(100));
        assertTrue(id.length() <= WorkflowInstanceAdminRules.OPERATION_ID_MAX_LENGTH);
        assertTrue(id.endsWith("S".repeat(10)), "超长后缀截尾保留，唯一性仍在");
        assertTrue(id.startsWith("migrate-"), "前缀仍保留可读动作信息");
    }
}
