package com.acme.scaffold.workflow.bpmn;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BPMN 构建器纯单元测试：验证生成 XML 可被 Flowable 回解析（合法 BPMN），且包含预期的节点/连线/多实例/条件分支。
 * 不依赖数据库，可在沙箱真实运行。
 */
class BpmnWorkflowBuilderTest {

    private WorkflowNodeDTO node(String id, WorkflowNodeType type, String name, String approvalMode,
                                String assigneeType, String assigneeExpression, String rejectPolicy) {
        return new WorkflowNodeDTO(id, type, name, approvalMode, BigDecimal.ONE, assigneeType,
                assigneeExpression, 1, 0, null, rejectPolicy, null, null, null);
    }

    private WorkflowEdgeDTO edge(String id, String src, String tgt, String cond, Boolean isDefault) {
        return new WorkflowEdgeDTO(id, src, tgt, cond, isDefault);
    }

    private BpmnModel parse(String xml) {
        return new BpmnXMLConverter().convertToBpmnModel(
                () -> new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), false, false);
    }

    @Test
    void buildsValidBpmnForFullDesign() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("n1", WorkflowNodeType.START, "发起", null, null, null, null),
                node("a1", WorkflowNodeType.APPROVAL, "主管审批", "ANY", "USER", "10,11", "END"),
                node("a2", WorkflowNodeType.APPROVAL, "总监审批", "ALL", "ROLE", "DIRECTOR", "NONE"),
                node("g1", WorkflowNodeType.GATEWAY, "金额分支", null, null, null, null),
                node("s1", WorkflowNodeType.SERVICE, "同步ERP", null, null, null, null),
                node("c1", WorkflowNodeType.CC, "抄送财务", null, null, null, null),
                node("n2", WorkflowNodeType.END, "结束", null, null, null, null));
        List<WorkflowEdgeDTO> edges = List.of(
                edge("e1", "n1", "a1", null, null),
                edge("e2", "a1", "a2", null, null),
                edge("e3", "a2", "g1", null, null),
                edge("e4", "g1", "s1", "amount <= 1000", null),
                edge("e5", "g1", "c1", null, true),
                edge("e6", "s1", "n2", null, null),
                edge("e7", "c1", "n2", null, null));

        String xml = BpmnWorkflowBuilder.build("testFlow", "测试流程", nodes, edges);

        BpmnModel model = parse(xml);
        Process process = model.getMainProcess();
        assertNotNull(process);
        assertEquals("testFlow", process.getId());

        // 2 个审批用户任务 + 多实例
        List<UserTask> userTasks = process.getFlowElements().stream()
                .filter(e -> e instanceof UserTask).map(e -> (UserTask) e).toList();
        assertEquals(2, userTasks.size());
        UserTask a1 = userTasks.stream().filter(t -> "a1".equals(t.getId())).findFirst().orElseThrow();
        MultiInstanceLoopCharacteristics mi = a1.getLoopCharacteristics();
        assertNotNull(mi);
        assertEquals("node_a1_assignees", mi.getInputDataItem());
        assertEquals("${rejected_a1 == true || nrOfCompletedInstances >= node_a1_threshold}",
                mi.getCompletionCondition());

        // 2 个服务任务（执行步骤 + 抄送）
        List<ServiceTask> services = process.getFlowElements().stream()
                .filter(e -> e instanceof ServiceTask).map(e -> (ServiceTask) e).toList();
        assertEquals(2, services.size());

        // 条件分支网关 + 默认分支
        List<ExclusiveGateway> gateways = process.getFlowElements().stream()
                .filter(e -> e instanceof ExclusiveGateway).map(e -> (ExclusiveGateway) e).toList();
        // g1 + a1 的驳回网关 = 2
        assertEquals(2, gateways.size());
        ExclusiveGateway g1 = gateways.stream().filter(g -> "g1".equals(g.getId())).findFirst().orElseThrow();
        assertNotNull(g1.getDefaultFlow());

        // 驳回网关的条件边 rejected_a1 == true
        boolean hasRejectCond = process.getFlowElements().stream()
                .filter(e -> e instanceof org.flowable.bpmn.model.SequenceFlow)
                .map(e -> (org.flowable.bpmn.model.SequenceFlow) e)
                .anyMatch(sf -> "${rejected_a1 == true}".equals(sf.getConditionExpression()));
        assertTrue(hasRejectCond);

        // 统一的驳回终点
        assertTrue(process.getFlowElements().stream().anyMatch(e -> e instanceof EndEvent
                && "rejectedEnd".equals(e.getId())));
    }

    @Test
    void rejectsDesignWithTwoStartNodes() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("n1", WorkflowNodeType.START, "发起", null, null, null, null),
                node("n2", WorkflowNodeType.START, "发起2", null, null, null, null),
                node("e", WorkflowNodeType.END, "结束", null, null, null, null));
        List<WorkflowEdgeDTO> edges = List.of(edge("e1", "n1", "e", null, null),
                edge("e2", "n2", "e", null, null));
        assertThrows(BusinessException.class,
                () -> BpmnWorkflowBuilder.build("badFlow", "坏流程", nodes, edges));
    }

    @Test
    void rejectsUnreachableEnd() {
        List<WorkflowNodeDTO> nodes = List.of(
                node("n1", WorkflowNodeType.START, "发起", null, null, null, null),
                node("e", WorkflowNodeType.END, "结束", null, null, null, null));
        // n1 无出边 -> 死路 + 不可达 END
        List<WorkflowEdgeDTO> edges = List.of();
        assertThrows(BusinessException.class,
                () -> BpmnWorkflowBuilder.build("deadFlow", "死路流程", nodes, edges));
    }
}
