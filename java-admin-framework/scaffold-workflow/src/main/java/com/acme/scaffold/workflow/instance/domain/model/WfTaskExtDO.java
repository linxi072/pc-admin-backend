package com.acme.scaffold.workflow.instance.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class WfTaskExtDO {

    private Long id;

    private Long tenantId = 0L;
    private String taskId;
    private String processInstanceId;
    private String activityId;
    /** 活动名称快照：任务创建期从引擎捕获，已办列表无需回查引擎即可展示可读节点名。 */
    private String activityName;
    private Long assigneeUserId;
    private Long originalAssigneeUserId;
    /** 委派发起人（原审批人）：任务被委派给 {@code assigneeUserId} 期间记录真正的负责人，便于办结交还。 */
    private Long delegatorUserId;
    /** 委派状态：PENDING=已委派待办、RESOLVED=被委托人已办结交还、null=非委派任务。 */
    private String delegationState;
    private String status;
    private String approvalMode;
    private LocalDateTime dueAt;
    private LocalDateTime claimedAt;
    private LocalDateTime completedAt;

    private Integer version = 0;
}
