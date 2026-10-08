package com.acme.scaffold.workflow.bootstrap;

import com.acme.scaffold.workflow.bpmn.BpmnWorkflowBuilder;
import com.acme.scaffold.workflow.dto.design.SaveDesignRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验内置工作流种子：leaveApproval 与 expense 的设计节点/连线均可经 {@link BpmnWorkflowBuilder}
 * 编译为合法 BPMN 2.0，且关键元素（多实例受让人、会签阈值、驳回网关条件、结束节点）齐备。
 * 不依赖数据库，可在沙箱离线运行。新增内置流程时，只需在 BuiltinWorkflowSeeder.seedAll 中追加一条，
 * 并视情况补充此处的断言。
 */
class BuiltinWorkflowSeederTest {

    @Test
    void leaveApprovalSeedBuildsValidBpmn() {
        SaveDesignRequest req = BuiltinWorkflowSeeder.buildLeaveApprovalDesign("请假审批");
        List<?> nodes = req.nodes();
        List<?> edges = req.edges();
        assertEquals(4, nodes.size(), "应包含 start/countersign/manager/approvedEnd");
        assertEquals(3, edges.size());

        String bpmn = BpmnWorkflowBuilder.build(BuiltinWorkflowSeeder.LEAVE_APPROVAL_KEY,
                req.processName(), req.nodes(), req.edges());
        assertNotNull(bpmn);
        assertTrue(bpmn.contains("node_countersign_assignees"), "应包含会签受让人集合变量");
        assertTrue(bpmn.contains("node_countersign_threshold"), "应包含会签阈值变量");
        assertTrue(bpmn.contains("rejected_countersign == true"), "应包含会签驳回条件");
        assertTrue(bpmn.contains("rejected_manager == true"), "应包含主管驳回条件");
        assertTrue(bpmn.contains("approvedEnd"), "应包含审批通过结束节点");
        assertTrue(bpmn.contains("rejectedEnd"), "构建器应统一注入驳回结束节点");
    }

    @Test
    void expenseSeedBuildsValidBpmn() {
        SaveDesignRequest req = BuiltinWorkflowSeeder.buildExpenseDesign("报销审批");
        List<?> nodes = req.nodes();
        List<?> edges = req.edges();
        assertEquals(4, nodes.size(), "应包含 start/deptManager/finance/approvedEnd");
        assertEquals(3, edges.size());
        // 受让人解析方式：部门主管取发起人主管，财务取角色
        assertTrue(req.nodes().stream().anyMatch(n -> "deptManager".equals(n.id())
                && "INITIATOR_MANAGER".equals(n.assigneeType())), "部门主管应为 INITIATOR_MANAGER");
        assertTrue(req.nodes().stream().anyMatch(n -> "finance".equals(n.id())
                && "ROLE".equals(n.assigneeType()) && "FINANCE".equals(n.assigneeExpression())),
                "财务应为 ROLE=FINANCE");

        String bpmn = BpmnWorkflowBuilder.build(BuiltinWorkflowSeeder.EXPENSE_KEY,
                req.processName(), req.nodes(), req.edges());
        assertNotNull(bpmn);
        assertTrue(bpmn.contains("node_deptManager_assignees"), "应包含部门主管受让人集合变量");
        assertTrue(bpmn.contains("node_finance_assignees"), "应包含财务受让人集合变量");
        assertTrue(bpmn.contains("rejected_deptManager == true"), "应包含部门主管驳回条件");
        assertTrue(bpmn.contains("rejected_finance == true"), "应包含财务驳回条件");
        assertTrue(bpmn.contains("approvedEnd"), "应包含审批通过结束节点");
        assertTrue(bpmn.contains("rejectedEnd"), "构建器应统一注入驳回结束节点");
    }

    @Test
    void seedKeysAreDistinct() {
        // 确保各内置流程 processKey 不冲突，避免部署互相覆盖
        assertTrue(!BuiltinWorkflowSeeder.LEAVE_APPROVAL_KEY.equals(BuiltinWorkflowSeeder.EXPENSE_KEY));
    }
}
