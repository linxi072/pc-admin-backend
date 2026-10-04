package com.acme.scaffold.workflow.port;

import java.util.List;

/**
 * 工作流引擎端口：屏蔽 Flowable 具体 API，业务层只依赖该接口，便于替换或测试。
 */
public interface WorkflowEnginePort {

    record StartedInstance(String processInstanceId, String processDefinitionId) {
    }

    record TaskInfo(String taskId, String executionId, String processInstanceId, String activityId,
                    String name, Long assigneeUserId, java.time.LocalDateTime dueAt) {
    }

    /**
     * 启动流程实例。
     *
     * @param processKey        流程定义 key（如 leaveApproval）
     * @param businessId        业务单据 ID（作为 businessKey）
     * @param title             审批标题
     * @param starterUserId     发起人
     * @param starterOrgId      发起人机构
     * @param assigneeUserIds   会签/或签审批人（多实例）
     * @param managerUserId     主管审批人
     * @param approvalMode      会签模式：ALL（全部通过）或 ANY（任一通过）
     */
    StartedInstance start(String processKey, String businessId, String title, Long starterUserId,
                          Long starterOrgId, List<Long> assigneeUserIds, Long managerUserId, String approvalMode);

    TaskInfo getTask(String taskId);

    List<TaskInfo> listTasksByAssignee(Long userId);

    List<TaskInfo> activeTasks(String processInstanceId);

    void complete(String taskId);

    /** 标记流程变量 rejected=true，驱动多实例提前结束并走驳回分支。 */
    void setRejected(String executionId);

    void setAssignee(String taskId, Long userId);

    String status(String processInstanceId);
}
