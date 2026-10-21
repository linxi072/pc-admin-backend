package com.acme.scaffold.workflow.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 发起审批流程入参。
 *
 * <p>自工作流改造为「可编辑设计」后，受让人/审批模式/驳回策略均由已发布设计的节点配置经
 * {@code AssigneeResolver} 解析，因此 {@code assigneeUserIds}/{@code managerUserId}/{@code approvalMode}
 * 不再是必填（仅在发起非设计驱动的遗留流程时可选传入，作为兜底）。
 * {@code formFields} 透传给流程变量，供执行步骤/抄送/条件分支读取。</p>
 */
public record StartProcessRequest(
        @NotBlank String processKey,
        @NotBlank String businessType,
        @NotBlank String businessId,
        @NotBlank String title,
        List<Long> assigneeUserIds,
        Long managerUserId,
        String approvalMode,
        Map<String, Object> formFields) {
}
