package com.acme.scaffold.workflow.instance.dto;

import com.acme.scaffold.workflow.support.ApprovalAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 审批/驳回任务入参。operationId 为幂等键，防止重复提交。
 */
public record CompleteTaskRequest(
        @NotBlank String taskId,
        @NotNull ApprovalAction action,
        String opinion,
        @NotBlank String operationId) {
}
