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
     * 部署 BPMN XML 为新流程定义版本（同一 processKey 多次部署形成版本链）。
     *
     * @return deploymentId
     */
    String deploy(String processKey, String processName, String bpmnXml);

    /** 查询某 processKey 的最新流程定义 id（用于回写部署信息）。 */
    String latestProcessDefinitionId(String processKey);

    /**
     * 启动流程实例。
     *
     * @param processKey        流程定义 key（如 leaveApproval 或自定义工作流 key）
     * @param businessId        业务单据 ID（作为 businessKey）
     * @param title             审批标题
     * @param starterUserId     发起人
     * @param starterOrgId      发起人机构
     * @param variables         流程变量（含节点受让人、阈值、表单字段、rejected 标记等）
     */
    StartedInstance start(String processKey, String businessId, String title, Long starterUserId,
                          Long starterOrgId, java.util.Map<String, Object> variables);

    TaskInfo getTask(String taskId);

    List<TaskInfo> listTasksByAssignee(Long userId);

    List<TaskInfo> activeTasks(String processInstanceId);

    void complete(String taskId);

    /**
     * 设置指定驳回变量为 true，驱动对应节点的多实例提前结束并走驳回分支。
     * 自定义工作流按节点使用 {@code rejected_<nodeId>}，内置请假流程使用 {@code rejected}。
     */
    void setRejected(String executionId, String variableName);

    void setAssignee(String taskId, Long userId);

    String status(String processInstanceId);
}
