package com.acme.scaffold.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不可变审批记录。一旦写入不更新，用于审计与流程回溯。
 */
@Data
public class WfApprovalRecordDO {

    private Long id;

    private Long tenantId = 0L;
    private String operationId;
    private String processInstanceId;
    private String taskId;
    private String activityId;
    private String action;
    private Long operatorUserId;
    private Long fromUserId;
    private Long toUserId;
    private String opinion;
    private String attachmentRefs;
    private String snapshot;
    private String traceId;
    private LocalDateTime occurredAt;
}
