package com.acme.scaffold.workflow.dto.design;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流设计视图（含节点、连线与已生成 BPMN）。
 */
public record WorkflowDesignView(
        Long id,
        String processKey,
        String processName,
        String description,
        String status,
        Integer version,
        String formSchema,
        List<WorkflowNodeDTO> nodes,
        List<WorkflowEdgeDTO> edges,
        String bpmnXml,
        String deploymentId,
        String processDefinitionId,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
