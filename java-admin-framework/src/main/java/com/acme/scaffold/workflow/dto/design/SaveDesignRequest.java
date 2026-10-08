package com.acme.scaffold.workflow.dto.design;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 保存工作流草稿（全量覆盖节点与连线）。发布前可多次保存。
 */
public record SaveDesignRequest(
        @NotBlank String processName,
        String description,
        String formSchema,
        @NotNull List<WorkflowNodeDTO> nodes,
        @NotNull List<WorkflowEdgeDTO> edges) {
}
