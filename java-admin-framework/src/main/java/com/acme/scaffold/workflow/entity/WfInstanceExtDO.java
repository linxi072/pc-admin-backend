package com.acme.scaffold.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfInstanceExtDO {

    private Long id;

    private Long tenantId = 0L;
    private String processInstanceId;
    private String processDefinitionId;
    private String businessType;
    private String businessId;
    private String title;
    private Long starterUserId;
    private Long starterOrgId;
    private String currentActivityId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer version = 0;
}
