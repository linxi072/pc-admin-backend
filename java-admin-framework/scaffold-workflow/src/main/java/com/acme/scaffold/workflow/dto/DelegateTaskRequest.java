package com.acme.scaffold.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 委派任务入参。operationId 为幂等键，防止重复提交导致任务被反复搬移。
 */
public record DelegateTaskRequest(
        @NotBlank String taskId,
        @NotNull Long toUserId,
        String opinion,
        @NotBlank String operationId) {
}
