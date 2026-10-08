package com.acme.scaffold.workflow.bpmn;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.dto.design.WorkflowEdgeDTO;
import com.acme.scaffold.workflow.dto.design.WorkflowNodeDTO;
import com.acme.scaffold.workflow.model.WorkflowNodeType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流设计图结构校验。发布前调用，保证生成的 BPMN 合法、可部署、可流转到底。
 */
public final class WorkflowDesignValidator {

    private WorkflowDesignValidator() {
    }

    public static void validate(String processKey, String processName,
                                List<WorkflowNodeDTO> nodes, List<WorkflowEdgeDTO> edges) {
        if (processKey == null || processKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "流程 key 不能为空");
        }
        if (!processKey.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "流程 key 需以字母开头且仅含字母/数字/下划线");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "至少包含一个节点");
        }
        if (edges == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "连线不能为 null");
        }

        Map<String, WorkflowNodeDTO> byId = nodes.stream()
                .collect(Collectors.toMap(WorkflowNodeDTO::id, Function.identity(), (a, b) -> a));

        // 节点 id 唯一且合法
        Set<String> seen = new HashSet<>();
        for (WorkflowNodeDTO n : nodes) {
            if (n.id() == null || !n.id().matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "节点 id 需以字母开头且仅含字母/数字/下划线: " + n.id());
            }
            if (!seen.add(n.id())) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "节点 id 重复: " + n.id());
            }
        }

        long startCount = nodes.stream().filter(n -> n.type() == WorkflowNodeType.START).count();
        long endCount = nodes.stream().filter(n -> n.type() == WorkflowNodeType.END).count();
        if (startCount != 1) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "必须且只能有一个发起(START)节点");
        }
        if (endCount < 1) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "至少需有一个结束(END)节点");
        }

        // 节点级配置校验
        for (WorkflowNodeDTO n : nodes) {
            if (n.type() == WorkflowNodeType.APPROVAL) {
                validateApproval(n);
            }
        }

        // 连线合法性
        Set<String> nodeIds = byId.keySet();
        for (WorkflowEdgeDTO e : edges) {
            if (!nodeIds.contains(e.sourceNodeId()) || !nodeIds.contains(e.targetNodeId())) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "连线引用了不存在的节点: " + e.sourceNodeId() + " -> " + e.targetNodeId());
            }
            if (byId.get(e.sourceNodeId()).type() == WorkflowNodeType.END) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "结束节点不能有出边: " + e.sourceNodeId());
            }
        }

        // 每个 GATEWAY 的多出边：除至多一条默认边外，其余必须有条件表达式
        Map<String, Long> outDegree = edges.stream()
                .collect(Collectors.groupingBy(WorkflowEdgeDTO::sourceNodeId, Collectors.counting()));
        for (WorkflowEdgeDTO e : edges) {
            WorkflowNodeDTO src = byId.get(e.sourceNodeId());
            if (src.type() == WorkflowNodeType.GATEWAY && outDegree.getOrDefault(e.sourceNodeId(), 0L) > 1) {
                boolean hasDefault = edges.stream().anyMatch(x ->
                        x.sourceNodeId().equals(e.sourceNodeId()) && Boolean.TRUE.equals(x.isDefault()));
                if (e.conditionExpression() == null || e.conditionExpression().isBlank()) {
                    if (!Boolean.TRUE.equals(e.isDefault())) {
                        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                                "条件分支节点 " + e.sourceNodeId() + " 的每条出边需配置条件表达式，或指定一条默认边");
                    }
                }
                if (hasDefault && edges.stream().filter(x -> x.sourceNodeId().equals(e.sourceNodeId())
                        && Boolean.TRUE.equals(x.isDefault())).count() > 1) {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                            "条件分支节点 " + e.sourceNodeId() + " 至多只能有一条默认边");
                }
            }
        }

        // 起始节点出度必须为 1
        String startId = nodes.stream().filter(n -> n.type() == WorkflowNodeType.START)
                .map(WorkflowNodeDTO::id).findFirst().orElse(null);
        if (outDegree.getOrDefault(startId, 0L) != 1) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "发起节点必须有且仅有一条出边");
        }

        // 非结束节点必须有出边（避免死路）
        for (WorkflowNodeDTO n : nodes) {
            if (n.type() == WorkflowNodeType.END) {
                continue;
            }
            if (outDegree.getOrDefault(n.id(), 0L) < 1) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "节点缺少出边（死路）: " + n.id());
            }
        }

        // 可达性：从 START 出发能到达某个 END
        validateReachEnd(startId, edges, byId);
    }

    private static void validateApproval(WorkflowNodeDTO n) {
        String mode = n.approvalMode() == null ? "ANY" : n.approvalMode().toUpperCase();
        if (!Set.of("ALL", "ANY", "RATIO").contains(mode)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "审批节点 " + n.id() + " 的审批模式非法: " + n.approvalMode());
        }
        if ("RATIO".equals(mode)) {
            if (n.approvalRatio() == null || n.approvalRatio().compareTo(java.math.BigDecimal.ZERO) <= 0
                    || n.approvalRatio().compareTo(java.math.BigDecimal.ONE) > 0) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "审批节点 " + n.id() + " 的通过比例需介于 (0,1]");
            }
        }
        String at = n.assigneeType() == null ? "USER" : n.assigneeType().toUpperCase();
        if (!Set.of("USER", "ROLE", "ORG", "INITIATOR", "INITIATOR_MANAGER").contains(at)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "审批节点 " + n.id() + " 的受让人类型非法: " + n.assigneeType());
        }
        if ("USER".equals(at) && (n.assigneeExpression() == null || n.assigneeExpression().isBlank())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "审批节点 " + n.id() + " 指定人员(USER)时必填受让人表达式（逗号分隔的用户ID）");
        }
        String rp = n.rejectPolicy() == null ? "NONE" : n.rejectPolicy().toUpperCase();
        if (!Set.of("NONE", "PREVIOUS", "END", "SPECIFIC").contains(rp)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "审批节点 " + n.id() + " 的驳回策略非法: " + n.rejectPolicy());
        }
    }

    private static void validateReachEnd(String startId, List<WorkflowEdgeDTO> edges,
                                         Map<String, WorkflowNodeDTO> byId) {
        Set<String> visited = new HashSet<>();
        Set<String> frontier = new HashSet<>();
        frontier.add(startId);
        boolean reachedEnd = false;
        while (!frontier.isEmpty()) {
            Set<String> next = new HashSet<>();
            for (String cur : frontier) {
                if (visited.contains(cur)) {
                    continue;
                }
                visited.add(cur);
                WorkflowNodeDTO node = byId.get(cur);
                if (node != null && node.type() == WorkflowNodeType.END) {
                    reachedEnd = true;
                }
                for (WorkflowEdgeDTO e : edges) {
                    if (e.sourceNodeId().equals(cur)) {
                        next.add(e.targetNodeId());
                    }
                }
            }
            frontier = next;
        }
        if (!reachedEnd) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "从发起节点无法到达任何结束节点");
        }
    }
}
