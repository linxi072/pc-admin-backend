package com.acme.scaffold.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WfTaskExtDO {

    private Long id;

    private Long tenantId = 0L;
    private String taskId;
    private String processInstanceId;
    private String activityId;
    private Long assigneeUserId;
    private Long originalAssigneeUserId;
    private String status;
    private String approvalMode;
    private LocalDateTime dueAt;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;

    private Integer version = 0;
}
