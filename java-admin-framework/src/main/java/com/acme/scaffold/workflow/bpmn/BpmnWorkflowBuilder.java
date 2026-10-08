package com.acme.scaffold.workflow.bpmn;

import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将设计态的「节点 + 连线」图编译为合法 BPMN 2.0 XML，再经 Flowable 部署为新版本流程定义。
 *
 * <p>映射规则：
 * <ul>
 *   <li>START → startEvent（固定 id {@code start}）。</li>
 *   <li>END → endEvent（id = 节点 id）。</li>
 *   <li>APPROVAL → userTask + 多实例（会签/或签/比例），受让人由变量
 *       {@code node_<id>_assignee} 在发起时注入；并在节点后插入「驳回判定」排他网关，
 *       条件 {@code rejected_<id> == true} 时流向驳回目标（上一审批/结束/指定节点）。</li>
 *   <li>SERVICE → serviceTask（GenericStepDelegate 自动执行步骤）。</li>
 *   <li>CC → serviceTask（CcStepDelegate 抄送步骤）。</li>
 *   <li>GATEWAY → exclusiveGateway，出边携带条件表达式，至多一条默认边。</li>
 * </ul>
 * 每次发布都会生成独立 XML，部署后形成新版本；新发起的实例自动使用最新版本（实时生效），
 * 已在途的实例沿用其原版本，互不影响。
 */
public class BpmnWorkflowBuilder {

    private BpmnWorkflowBuilder() {
    }

    public static String build(String processKey, String processName,
                               List<WorkflowNodeDTO> nodes, List<WorkflowEdgeDTO> edges) {
        WorkflowDesignValidator.validate(processKey, processName, nodes, edges);

        BpmnModel model = new BpmnModel();
        model.setTargetNamespace("http://acme/scaffold/workflow");
        Process process = new Process();
        process.setId(sanitizeId(processKey));
        process.setName(processName);
        process.setExecutable(true);
        model.addProcess(process);

        String startNodeId = nodes.stream().filter(n -> n.type() == WorkflowNodeType.START)
                .map(WorkflowNodeDTO::id).findFirst().orElse(null);

        Map<String, String> rejectGwOf = new HashMap<>();
        Map<String, String> approvalNextFlowOf = new HashMap<>();

        for (WorkflowNodeDTO node : nodes) {
            switch (node.type()) {
                case START -> {
                    StartEvent se = new StartEvent();
                    se.setId("start");
                    se.setName(orDefault(node.name(), "发起"));
                    process.addFlowElement(se);
                }
                case END -> {
                    EndEvent ee = new EndEvent();
                    ee.setId(node.id());
                    ee.setName(orDefault(node.name(), "结束"));
                    process.addFlowElement(ee);
                }
                case APPROVAL -> {
                    UserTask ut = new UserTask();
                    ut.setId(node.id());
                    ut.setName(orDefault(node.name(), "审批"));
                    ut.setAssignee("${node_" + node.id() + "_assignee}");
                    MultiInstanceLoopCharacteristics mi = new MultiInstanceLoopCharacteristics();
                    mi.setInputDataItem("node_" + node.id() + "_assignees");
                    mi.setElementVariable("node_" + node.id() + "_assignee");
                    mi.setSequential(false);
                    mi.setCompletionCondition("${rejected_" + node.id()
                            + " == true || nrOfCompletedInstances >= node_" + node.id() + "_threshold}");
                    ut.setLoopCharacteristics(mi);
                    process.addFlowElement(ut);
                    if (!"NONE".equalsIgnoreCase(orDefault(node.rejectPolicy(), "NONE"))) {
                        ExclusiveGateway rg = new ExclusiveGateway();
                        rg.setId(node.id() + "_rejectgw");
                        rg.setName(orDefault(node.name(), "审批") + "-驳回判定");
                        process.addFlowElement(rg);
                        rejectGwOf.put(node.id(), node.id() + "_rejectgw");
                    }
                }
                case SERVICE -> {
                    ServiceTask st = new ServiceTask();
                    st.setId(node.id());
                    st.setName(orDefault(node.name(), "执行步骤"));
                    st.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
                    st.setImplementation("com.acme.scaffold.workflow.adapter.step.GenericStepDelegate");
                    process.addFlowElement(st);
                }
                case CC -> {
                    ServiceTask st = new ServiceTask();
                    st.setId(node.id());
                    st.setName(orDefault(node.name(), "抄送"));
                    st.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
                    st.setImplementation("com.acme.scaffold.workflow.adapter.step.CcStepDelegate");
                    process.addFlowElement(st);
                }
                case GATEWAY -> {
                    ExclusiveGateway gw = new ExclusiveGateway();
                    gw.setId(node.id());
                    gw.setName(orDefault(node.name(), "条件分支"));
                    process.addFlowElement(gw);
                }
            }
        }

        // 统一驳回终点
        EndEvent rejectedEnd = new EndEvent();
        rejectedEnd.setId("rejectedEnd");
        rejectedEnd.setName("审批驳回");
        process.addFlowElement(rejectedEnd);

        // 连线
        for (WorkflowEdgeDTO edge : edges) {
            String src = edge.sourceNodeId();
            if (src.equals(startNodeId)) {
                src = "start";
            }
            if (rejectGwOf.containsKey(edge.sourceNodeId())) {
                src = rejectGwOf.get(edge.sourceNodeId());
                approvalNextFlowOf.put(edge.sourceNodeId(), "flow_" + edge.id());
            }
            SequenceFlow sf = new SequenceFlow();
            sf.setId("flow_" + edge.id());
            sf.setSourceRef(src);
            sf.setTargetRef(edge.targetNodeId());
            if (edge.conditionExpression() != null && !edge.conditionExpression().isBlank()) {
                sf.setConditionExpression("${" + edge.conditionExpression() + "}");
            }
            process.addFlowElement(sf);

            if (Boolean.TRUE.equals(edge.isDefault())) {
                FlowElement fe = process.getFlowElement(edge.sourceNodeId());
                if (fe instanceof ExclusiveGateway gw) {
                    gw.setDefaultFlow("flow_" + edge.id());
                }
            }
        }

        // 驳回网关的两条出边：正常流转(默认) + 驳回流转(条件)
        for (WorkflowNodeDTO node : nodes) {
            String rgId = rejectGwOf.get(node.id());
            if (rgId == null) {
                continue;
            }
            String nextFlowId = approvalNextFlowOf.getOrDefault(node.id(), "flow_" + node.id() + "_next");
            // task -> reject gateway
            SequenceFlow toRg = new SequenceFlow();
            toRg.setId(node.id() + "_to_rg");
            toRg.setSourceRef(node.id());
            toRg.setTargetRef(rgId);
            process.addFlowElement(toRg);
            // reject gateway -> normal next (default)
            SequenceFlow normal = new SequenceFlow();
            normal.setId(nextFlowId);
            normal.setSourceRef(rgId);
            normal.setTargetRef(rejectGwNormalTarget(edges, node.id(), startNodeId));
            process.addFlowElement(normal);
            ExclusiveGateway rg = (ExclusiveGateway) process.getFlowElement(rgId);
            rg.setDefaultFlow(nextFlowId);
            // reject gateway -> reject target (condition)
            SequenceFlow reject = new SequenceFlow();
            reject.setId(node.id() + "_rg_reject");
            reject.setSourceRef(rgId);
            reject.setTargetRef(resolveRejectTarget(node, nodes, startNodeId));
            reject.setConditionExpression("${rejected_" + node.id() + " == true}");
            process.addFlowElement(reject);
        }

        byte[] xml = new BpmnXMLConverter().convertToXML(model, "UTF-8");
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static String rejectGwNormalTarget(List<WorkflowEdgeDTO> edges, String nodeId, String startNodeId) {
        return edges.stream()
                .filter(e -> e.sourceNodeId().equals(nodeId))
                .map(WorkflowEdgeDTO::targetNodeId)
                .findFirst()
                .orElse("rejectedEnd");
    }

    private static String resolveRejectTarget(WorkflowNodeDTO node, List<WorkflowNodeDTO> nodes, String startNodeId) {
        String policy = orDefault(node.rejectPolicy(), "NONE").toUpperCase();
        return switch (policy) {
            case "END" -> "rejectedEnd";
            case "SPECIFIC" -> {
                String target = node.rejectTargetNodeId();
                yield (target == null || target.equals(startNodeId)) ? "rejectedEnd" : target;
            }
            case "PREVIOUS" -> previousApprovalId(node, nodes).orElse("rejectedEnd");
            default -> "rejectedEnd";
        };
    }

    private static Optional<String> previousApprovalId(WorkflowNodeDTO current, List<WorkflowNodeDTO> nodes) {
        String found = null;
        for (WorkflowNodeDTO n : nodes) {
            if (n == current) {
                break;
            }
            if (n.type() == WorkflowNodeType.APPROVAL) {
                found = n.id();
            }
        }
        return Optional.ofNullable(found);
    }

    private static String sanitizeId(String key) {
        String s = key.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) {
            return "process";
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "p_" + s;
        }
        return s;
    }

    private static String orDefault(String value, String def) {
        return (value == null || value.isBlank()) ? def : value;
    }
}
