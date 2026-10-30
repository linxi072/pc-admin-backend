package com.acme.scaffold.workflow.bootstrap;

import com.acme.scaffold.workflow.bpmn.BpmnWorkflowBuilder;
import com.acme.scaffold.workflow.dto.design.SaveDesignRequest;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验内置 leaveApproval 种子设计：节点/连线可经 {@link BpmnWorkflowBuilder} 编译为合法 BPMN 2.0，
 * 且关键元素（多实例受让人、会签阈值、驳回网关条件、结束节点）齐备。该测试不依赖数据库，可在沙箱离线运行。
 */
class LeaveApprovalDesignSeedTest {

    private SaveDesignRequest design() {
        return BuiltinWorkflowSeeder.buildLeaveApprovalDesign("请假审批（含会签/或签/驳回）");
    }

    @Test
    void seedNodesAndEdgesAreWellFormed() {
        SaveDesignRequest req = design();
        List<WorkflowNodeDTO> nodes = req.nodes();
        List<WorkflowEdgeDTO> edges = req.edges();

        assertEquals(4, nodes.size(), "应包含 start/countersign/manager/approvedEnd");
        assertEquals(1, nodes.stream().filter(n -> n.type().name().equals("START")).count());
        assertEquals(1, nodes.stream().filter(n -> n.type().name().equals("END")).count());
        assertEquals(3, edges.size());

        // 节点 id 稳定可读
        assertTrue(nodes.stream().anyMatch(n -> n.id().equals("countersign")));
        assertTrue(nodes.stream().anyMatch(n -> n.id().equals("manager")));
        assertTrue(nodes.stream().anyMatch(n -> n.id().equals("approvedEnd")));
    }

    @Test
    void seedBuildsValidBpmn() {
        SaveDesignRequest req = design();
        // build 内部会调用 WorkflowDesignValidator，非法图会在此抛错
        String bpmn = BpmnWorkflowBuilder.build(BuiltinWorkflowSeeder.LEAVE_APPROVAL_KEY,
                req.processName(), req.nodes(), req.edges());

        assertNotNull(bpmn);
        // 多实例受让人变量（集合 + 元素 + 阈值）
        assertTrue(bpmn.contains("node_countersign_assignees"), "应包含会签受让人集合变量");
        assertTrue(bpmn.contains("node_countersign_assignee"), "应包含会签受让人元素变量");
        assertTrue(bpmn.contains("node_countersign_threshold"), "应包含会签阈值变量");
        // 会签完成条件：驳回或全部通过
        assertTrue(bpmn.contains("rejected_countersign == true"), "应包含会签驳回条件");
        // 主管节点同理
        assertTrue(bpmn.contains("rejected_manager == true"), "应包含主管驳回条件");
        // 结束节点
        assertTrue(bpmn.contains("approvedEnd"), "应包含审批通过结束节点");
        assertTrue(bpmn.contains("rejectedEnd"), "构建器应统一注入驳回结束节点");
        // 默认分支（正常流转）存在
        assertTrue(bpmn.contains("default"), "驳回网关应有默认（正常）分支");
    }
}
