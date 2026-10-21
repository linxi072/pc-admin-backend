package com.acme.scaffold.workflow.bpmn;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkflowDesignValidator} 单元测试。
 *
 * <p>重点覆盖 2026-10-19 测试轮次发现并修复的两类缺陷：
 * 节点 id 为 null 时先抛 NPE（500）而非友好 400；非条件分支节点多出边不在发布期拦截，
 * 导致部署通过但运行时流转卡死。</p>
 */
class WorkflowDesignValidatorTest {

    private static WorkflowNodeDTO node(String id, WorkflowNodeType type) {
        return new WorkflowNodeDTO(id, type, id, null, null,
                "USER", "1", null, null, null, null, null, null, null);
    }

    private static WorkflowNodeDTO gateway(String id) {
        return new WorkflowNodeDTO(id, WorkflowNodeType.GATEWAY, id, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static WorkflowEdgeDTO edge(String id, String from, String to) {
        return new WorkflowEdgeDTO(id, from, to, null, null);
    }

    private static WorkflowEdgeDTO condEdge(String id, String from, String to, String expr) {
        return new WorkflowEdgeDTO(id, from, to, expr, null);
    }

    @Test
    @DisplayName("合法线性设计（START→APPROVAL→END）通过校验")
    void validLinearDesign_passes() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", WorkflowNodeType.START),
                node("a", WorkflowNodeType.APPROVAL),
                node("e", WorkflowNodeType.END));
        List<WorkflowEdgeDTO> edges = List.of(edge("l1", "s", "a"), edge("l2", "a", "e"));

        assertDoesNotThrow(() -> WorkflowDesignValidator.validate("leave", "请假", nodes, edges));
    }

    @Test
    @DisplayName("节点 id 为 null：返回 400 校验错误而非 NPE（修复前 toMap 先抛 NPE）")
    void nullNodeId_rejectedAsValidationError() {
        List<WorkflowNodeDTO> nodes = List.of(
                node(null, WorkflowNodeType.START),
                node("e", WorkflowNodeType.END));
        List<WorkflowEdgeDTO> edges = List.of(edge("l1", null, "e"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> WorkflowDesignValidator.validate("leave", "请假", nodes, edges));
        assertTrue(ex.getMessage().contains("节点 id"), "应提示节点 id 非法，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("非条件分支节点（APPROVAL）多条出边：发布期直接拒绝（修复前部署后运行时卡死）")
    void nonGatewayMultiOutEdge_rejected() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", WorkflowNodeType.START),
                node("a", WorkflowNodeType.APPROVAL),
                node("e1", WorkflowNodeType.END),
                node("e2", WorkflowNodeType.END));
        List<WorkflowEdgeDTO> edges = List.of(
                edge("l1", "s", "a"),
                edge("l2", "a", "e1"),
                edge("l3", "a", "e2"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> WorkflowDesignValidator.validate("leave", "请假", nodes, edges));
        assertTrue(ex.getMessage().contains("只能有一条出边"), "实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("GATEWAY 多条带条件出边仍然合法（既有能力不受影响）")
    void gatewayMultiConditionalEdges_passes() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", WorkflowNodeType.START),
                gateway("g"),
                node("e1", WorkflowNodeType.END),
                node("e2", WorkflowNodeType.END));
        List<WorkflowEdgeDTO> edges = List.of(
                edge("l1", "s", "g"),
                condEdge("l2", "g", "e1", "${amount > 100}"),
                condEdge("l3", "g", "e2", "${amount <= 100}"));

        assertDoesNotThrow(() -> WorkflowDesignValidator.validate("expense", "报销", nodes, edges));
    }

    @Test
    @DisplayName("带驳回策略的审批节点允许多出边（构建器自动插入驳回网关改接）")
    void approvalWithRejectPolicy_multiOutEdges_passes() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("s", WorkflowNodeType.START),
                new WorkflowNodeDTO("a", WorkflowNodeType.APPROVAL, "主管审批", "ANY", null,
                        "USER", "10,11", null, null, null, "END", null, null, null),
                node("e1", WorkflowNodeType.END),
                node("e2", WorkflowNodeType.END));
        List<WorkflowEdgeDTO> edges = List.of(
                edge("l1", "s", "a"),
                edge("l2", "a", "e1"),
                edge("l3", "a", "e2"));

        assertDoesNotThrow(() -> WorkflowDesignValidator.validate("rejectFlow", "驳回流程", nodes, edges));
    }
}
