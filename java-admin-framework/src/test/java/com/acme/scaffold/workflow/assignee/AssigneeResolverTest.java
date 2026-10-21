package com.acme.scaffold.workflow.assignee;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AssigneeResolver#validateConfig} 的 USER 分支单测。
 *
 * <p>2026-10-19 测试轮次修复：USER 受让人表达式含非数字时发布期不拦，
 * 发起流程时才以 NumberFormatException → 500 暴露。现在发布期即校验「逗号分隔数字」。
 * USER 分支为纯字符串校验，不触碰 DSLContext，故可传 null 构造。</p>
 */
class AssigneeResolverTest {

    private final AssigneeResolver resolver = new AssigneeResolver(null);

    private static WorkflowNodeDTO userNode(String expr) {
        return new WorkflowNodeDTO("a", null, "审批", null, null,
                "USER", expr, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("USER 受让人为逗号分隔数字：校验通过")
    void validNumericExpression_passes() {
        assertDoesNotThrow(() -> resolver.validateConfig(userNode("1, 22,333")));
    }

    @Test
    @DisplayName("USER 受让人为空：发布期拒绝")
    void blankExpression_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.validateConfig(userNode("  ")));
        assertTrue(ex.getMessage().contains("USER"), "实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("USER 受让人含非数字（如 abc）：发布期拒绝而非运行时 500")
    void nonNumericExpression_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.validateConfig(userNode("1,abc")));
        assertTrue(ex.getMessage().contains("用户ID非法"), "实际: " + ex.getMessage());
    }
}
