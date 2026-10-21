package com.acme.scaffold.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfDefinitionExtDO {

    private Long id;

    private Long tenantId = 0L;
    private String processKey;
    private String processName;
    private Integer version;
    private String deploymentId;
    private String processDefinitionId;
    private String formSchema;
    private String status;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
