package com.acme.scaffold.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 转办任务入参。
 */
public record TransferTaskRequest(
        @NotBlank String taskId,
        @NotNull Long toUserId,
        String opinion,
        @NotBlank String operationId) {
}
