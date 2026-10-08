package com.acme.scaffold.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流设计态实体，对应 {@code wf_workflow_design}。
 * 同一 processKey 可多次发布，每次发布版本号自增并生成新的 Flowable 流程定义（实时生效）。
 */
@Data
public class WfWorkflowDesignDO {

    private Long id;
    private Long tenantId = 0L;
    private String processKey;
    private String processName;
    private String description;
    private String status;
    private Integer version;
    private String nodesJson;
    private String edgesJson;
    private String formSchema;
    private String bpmnXml;
    private String deploymentId;
    private String processDefinitionId;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
