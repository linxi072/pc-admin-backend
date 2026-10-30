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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 与部署发布同款的 XSD 校验：重复元素 id / 悬空引用都会在此暴露。 */
    private void assertSchemaValid(String xml) throws Exception {
        new BpmnXMLConverter().validateModel(
                () -> new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** 收集进程内全部元素 id，重复即返回（BPMN 规范要求 id 全局唯一）。 */
    private Set<String> duplicatedElementIds(Process process) {
        Map<String, Integer> counts = new HashMap<>();
        for (org.flowable.bpmn.model.FlowElement fe : process.getFlowElements()) {
            counts.merge(fe.getId(), 1, Integer::sum);
        }
        Set<String> dups = new java.util.HashSet<>();
        counts.forEach((id, c) -> {
            if (c > 1) {
                dups.add(id);
            }
        });
        return dups;
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

    /**
     * 回归：审批节点配置驳回策略且有出边时，驳回网关循环曾以同一 flow_<edgeId> 重复新建
     * normal 流，导致 XML 元素 id 重复，部署发布时 Flowable XSD 校验（cvc-id.2）必现失败。
     * 本用例以「部署同款 XSD 校验 + 元素 id 唯一性」双重断言封堵该缺陷。
     */
    @Test
    void rejectGatewayWithOutgoingEdgeKeepsElementIdsUnique() throws Exception {
        List<WorkflowNodeDTO> nodes = List.of(
                node("n1", WorkflowNodeType.START, "发起", null, null, null, null),
                node("a1", WorkflowNodeType.APPROVAL, "主管审批", "ANY", "USER", "10,11", "END"),
                node("n2", WorkflowNodeType.END, "结束", null, null, null, null));
        List<WorkflowEdgeDTO> edges = List.of(edge("e1", "n1", "a1", null, null),
                edge("e2", "a1", "n2", null, null));

        String xml = BpmnWorkflowBuilder.build("rejectFlow", "驳回流程", nodes, edges);

        // 1) 部署同款 XSD 校验：旧缺陷在此处以 cvc-id.2 失败
        assertSchemaValid(xml);

        // 2) 元素 id 唯一性
        Process process = parse(xml).getMainProcess();
        assertTrue(duplicatedElementIds(process).isEmpty(),
                "BPMN 元素 id 重复: " + duplicatedElementIds(process));

        // 3) 驳回网关默认流必须引用连线阶段已有的 flow_e2，而非重复新建
        ExclusiveGateway rg = (ExclusiveGateway) process.getFlowElement("a1_rejectgw");
        assertNotNull(rg);
        assertEquals("flow_e2", rg.getDefaultFlow());
        org.flowable.bpmn.model.SequenceFlow e2 =
                (org.flowable.bpmn.model.SequenceFlow) process.getFlowElement("flow_e2");
        assertEquals("a1_rejectgw", e2.getSourceRef());
        assertEquals("n2", e2.getTargetRef());
    }

    /**
     * 多出边 + 显式默认边：设计指定第二条出边为默认时，
     * 驳回网关默认流必须是该显式边（旧实现会被首条边覆盖，语义错乱）。
     */
    @Test
    void rejectGatewayHonorsExplicitDefaultEdge() throws Exception {
        List<WorkflowNodeDTO> nodes = List.of(
                node("n1", WorkflowNodeType.START, "发起", null, null, null, null),
                node("a1", WorkflowNodeType.APPROVAL, "主管审批", "ANY", "USER", "10,11", "END"),
                node("n2", WorkflowNodeType.END, "结束", null, null, null, null),
                node("n3", WorkflowNodeType.END, "备选结束", null, null, null, null));
        List<WorkflowEdgeDTO> edges = List.of(edge("e1", "n1", "a1", null, null),
                edge("e2", "a1", "n2", null, null),
                edge("e3", "a1", "n3", null, true));

        String xml = BpmnWorkflowBuilder.build("rejectFlow2", "驳回流程2", nodes, edges);

        assertSchemaValid(xml);
        Process process = parse(xml).getMainProcess();
        assertTrue(duplicatedElementIds(process).isEmpty(),
                "BPMN 元素 id 重复: " + duplicatedElementIds(process));

        ExclusiveGateway rg = (ExclusiveGateway) process.getFlowElement("a1_rejectgw");
        assertEquals("flow_e3", rg.getDefaultFlow());
        // 两条出边均已改源到驳回网关
        org.flowable.bpmn.model.SequenceFlow f2 =
                (org.flowable.bpmn.model.SequenceFlow) process.getFlowElement("flow_e2");
        org.flowable.bpmn.model.SequenceFlow f3 =
                (org.flowable.bpmn.model.SequenceFlow) process.getFlowElement("flow_e3");
        assertEquals("a1_rejectgw", f2.getSourceRef());
        assertEquals("a1_rejectgw", f3.getSourceRef());
    }

    /** 现网 leaveApproval 同款结构（两个带驳回策略的审批串联）端到端可发布。 */
    @Test
    void chainedRejectPoliciesPublishCleanly() throws Exception {
        List<WorkflowNodeDTO> nodes = List.of(
                node("n1", WorkflowNodeType.START, "发起", null, null, null, null),
                node("a1", WorkflowNodeType.APPROVAL, "部门经理审批", "ALL", "ROLE", "DEPT_MANAGER", "PREVIOUS"),
                node("a2", WorkflowNodeType.APPROVAL, "财务审批", "ALL", "ROLE", "FINANCE", "END"),
                node("n2", WorkflowNodeType.END, "结束", null, null, null, null));
        List<WorkflowEdgeDTO> edges = List.of(edge("e1", "n1", "a1", null, null),
                edge("e2", "a1", "a2", null, null),
                edge("e3", "a2", "n2", null, null));

        String xml = BpmnWorkflowBuilder.build("leaveApproval", "请假审批", nodes, edges);

        assertSchemaValid(xml);
        Process process = parse(xml).getMainProcess();
        assertTrue(duplicatedElementIds(process).isEmpty(),
                "BPMN 元素 id 重复: " + duplicatedElementIds(process));
        // 两个驳回网关均存在且默认流分别引用各自出边
        assertEquals("flow_e2", ((ExclusiveGateway) process.getFlowElement("a1_rejectgw")).getDefaultFlow());
        assertEquals("flow_e3", ((ExclusiveGateway) process.getFlowElement("a2_rejectgw")).getDefaultFlow());
    }
}
