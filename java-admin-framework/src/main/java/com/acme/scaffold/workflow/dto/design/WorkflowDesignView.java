package com.acme.scaffold.workflow.dto.design;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流设计视图（含节点、连线与已生成 BPMN）。
 *
 * <p>id 为 18 位雪花 ID，超出 JS Number 安全整数范围（2^53-1），
 * 必须以字符串序列化下发，否则前端回传时精度丢失导致主键查无此行。
 */
public record WorkflowDesignView(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
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
