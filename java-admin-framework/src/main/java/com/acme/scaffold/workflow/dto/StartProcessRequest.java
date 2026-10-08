package com.acme.scaffold.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 发起审批流程入参。
 *
 * <p>自定义工作流发起时 {@code assigneeUserIds}/{@code managerUserId}/{@code approvalMode} 可省略，
 * 实际受让人由已发布设计的节点配置经 {@code AssigneeResolver} 解析；
 * {@code formFields} 透传给流程变量，供执行步骤/抄送/条件分支读取。</p>
 */
public record StartProcessRequest(
        @NotBlank String processKey,
        @NotBlank String businessType,
        @NotBlank String businessId,
        @NotBlank String title,
        @NotEmpty List<Long> assigneeUserIds,
        @NotNull Long managerUserId,
        String approvalMode,
        Map<String, Object> formFields) {
}
