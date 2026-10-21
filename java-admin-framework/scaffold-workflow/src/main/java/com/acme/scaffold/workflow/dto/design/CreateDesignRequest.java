package com.acme.scaffold.workflow.dto.design;

import jakarta.validation.constraints.NotBlank;

/**
 * 新建工作流草稿入参。processKey 作为流程定义 key，发布后用于发起实例。
 */
public record CreateDesignRequest(
        @NotBlank String processKey,
        @NotBlank String processName,
        String description) {
}
