package com.acme.scaffold.workflow.dto.design;

import com.acme.scaffold.workflow.model.WorkflowNodeType;

import java.math.BigDecimal;

/**
 * 工作流节点定义（设计态）。前端设计器与后端 BPMN 构建器之间的契约。
 *
 * <p>审批节点({@code APPROVAL})相关字段：approvalMode(ALL/ANY/RATIO)、approvalRatio、
 * assigneeType(USER/ROLE/ORG/INITIATOR/INITIATOR_MANAGER)、assigneeExpression、
 * allowTransfer/allowDelegate/timeoutMinutes、rejectPolicy(NONE/PREVIOUS/END/SPECIFIC)、
 * rejectTargetNodeId。</p>
 *
 * <p>执行步骤({@code SERVICE})相关字段：stepType、stepConfig。</p>
 */
public record WorkflowNodeDTO(
        String id,
        WorkflowNodeType type,
        String name,
        // 审批节点
        String approvalMode,
        BigDecimal approvalRatio,
        String assigneeType,
        String assigneeExpression,
        Integer allowTransfer,
        Integer allowDelegate,
        Integer timeoutMinutes,
        String rejectPolicy,
        String rejectTargetNodeId,
        // 执行步骤
        String stepType,
        String stepConfig) {
}
