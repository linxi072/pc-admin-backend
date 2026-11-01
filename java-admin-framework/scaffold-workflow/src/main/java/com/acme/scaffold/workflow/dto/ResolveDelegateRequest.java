package com.acme.scaffold.workflow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 办结委派（交还原审批人）入参。operationId 为幂等键，防止重复提交。
 */
public record ResolveDelegateRequest(
        @NotBlank String taskId,
        String opinion,
        @NotBlank String operationId) {
}
