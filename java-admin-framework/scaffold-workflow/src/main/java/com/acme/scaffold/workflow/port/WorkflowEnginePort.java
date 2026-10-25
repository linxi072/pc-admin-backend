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

    /**
     * 认领任务：把当前无办理人的池化任务绑定到指定用户。
     *
     * <p>若任务已被他人认领（引擎层 {@code assignee} 已非空且非该用户），
     * 实现应抛出 {@link com.acme.scaffold.common.exception.BusinessException}（CONFLICT），
     * 由业务层统一转 409。直接 assignee 非空的任务不适合走 claim，
     * 应使用 {@link #setAssignee}（转办语义）。
     */
    void claim(String taskId, Long userId);

    String status(String processInstanceId);

    /**
     * 分页查询已到期（dueDate 早于 {@code before}）的未完结任务，按到期时间升序（最紧急在前）。
     *
     * <p>供流程监控「超时任务」面使用：到期未办即超时，与业务扩展表 {@code wf_task_ext.due_at}
     * 回写值同源（BPMN 决定），以引擎为准。</p>
     *
     * @param before       到期时间上界（不含），一般为当前时刻
     * @param firstResult  偏移量（从 0 起）
     * @param maxResults   每页大小
     */
    List<TaskInfo> overdueTasks(java.time.LocalDateTime before, int firstResult, int maxResults);

    /** 已到期未完结任务总数（与 {@link #overdueTasks} 同条件），供分页 count。 */
    long countOverdueTasks(java.time.LocalDateTime before);

    /** 流程定义版本链条目（同 processKey 的每次部署形成一个版本）。 */
    record DefinitionVersion(String id, int version, String name, String key, boolean suspended,
                             String deploymentId, java.time.LocalDateTime deployTime) {
    }

    /**
     * 查询某 processKey 的流程定义版本链（版本号降序，最新在前）。
     *
     * <p>只读投影：挂起/激活等写操作不在此暴露（影响在途实例，风险高），仅用于监控展示。</p>
     */
    List<DefinitionVersion> definitionVersions(String processKey);
}
