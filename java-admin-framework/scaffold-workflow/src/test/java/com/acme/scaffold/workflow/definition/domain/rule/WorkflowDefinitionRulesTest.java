package com.acme.scaffold.workflow.definition.domain.rule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.definition.domain.rule.WorkflowDefinitionRules.DefinitionStateTransition;
import org.junit.jupiter.api.Test;

/** 流程定义挂起/激活状态机单测：转移判定与冲突异常映射。 */
class WorkflowDefinitionRulesTest {

    @Test
    void suspendTransitionTruthTable() {
        assertEquals(DefinitionStateTransition.SUSPEND_OK, WorkflowDefinitionRules.evaluateSuspend(false));
        assertEquals(DefinitionStateTransition.ALREADY_SUSPENDED, WorkflowDefinitionRules.evaluateSuspend(true));
    }

    @Test
    void activateTransitionTruthTable() {
        assertEquals(DefinitionStateTransition.ACTIVATE_OK, WorkflowDefinitionRules.evaluateActivate(true));
        assertEquals(DefinitionStateTransition.ALREADY_ACTIVE, WorkflowDefinitionRules.evaluateActivate(false));
    }

    @Test
    void okTransitionsShouldNotThrow() {
        assertDoesNotThrow(() -> WorkflowDefinitionRules.assertTransition(DefinitionStateTransition.SUSPEND_OK));
        assertDoesNotThrow(() -> WorkflowDefinitionRules.assertTransition(DefinitionStateTransition.ACTIVATE_OK));
    }

    @Test
    void repeatedSuspendShouldThrowConflict() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> WorkflowDefinitionRules.assertTransition(DefinitionStateTransition.ALREADY_SUSPENDED));
        assertTrue(ex.getMessage().contains("重复挂起"));
    }

    @Test
    void repeatedActivateShouldThrowConflict() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> WorkflowDefinitionRules.assertTransition(DefinitionStateTransition.ALREADY_ACTIVE));
        assertTrue(ex.getMessage().contains("重复激活"));
    }
}
