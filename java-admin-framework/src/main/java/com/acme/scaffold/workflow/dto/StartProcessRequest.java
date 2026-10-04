package com.acme.scaffold.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 发起审批流程入参。
 */
public record StartProcessRequest(
        @NotBlank String processKey,
        @NotBlank String businessType,
        @NotBlank String businessId,
        @NotBlank String title,
        @NotEmpty List<Long> assigneeUserIds,
        @NotNull Long managerUserId,
        String approvalMode) {
}
