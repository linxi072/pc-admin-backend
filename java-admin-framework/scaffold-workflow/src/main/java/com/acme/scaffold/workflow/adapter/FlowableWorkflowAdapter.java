package com.acme.scaffold.workflow.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.ProcessMigrationService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flowable 引擎适配：实现 {@link WorkflowEnginePort}。所有 Flowable API 调用集中在此，业务代码不直接依赖引擎类型。
 * 会签/或签通过流程变量 {@code assigneeList}、{@code approvalMode}、{@code rejected} 驱动 BPMN 多实例与网关。
 */
@Slf4j
@Component
public class FlowableWorkflowAdapter implements WorkflowEnginePort {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final ObjectProvider<ProcessMigrationService> processMigrationServiceProvider;

    public FlowableWorkflowAdapter(RuntimeService runtimeService, TaskService taskService,
                                   RepositoryService repositoryService, HistoryService historyService,
                                   ObjectProvider<ProcessMigrationService> processMigrationServiceProvider) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.processMigrationServiceProvider = processMigrationServiceProvider;
    }

    @Override
    public String deploy(String processKey, String processName, String bpmnXml) {
        Deployment deployment = repositoryService.createDeployment()
                .addString(processKey + ".bpmn20.xml", bpmnXml)
                .name(processName)
                .deploy();
        return deployment.getId();
    }

    @Override
    public String latestProcessDefinitionId(String processKey) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        if (pd == null) {
            throw new IllegalStateException("流程定义不存在: " + processKey);
        }
        return pd.getId();
    }

    @Override
    public StartedInstance start(String processKey, String businessId, String title, Long starterUserId,
                                Long starterOrgId, Map<String, Object> variables) {
        Map<String, Object> vars = new HashMap<>(variables == null ? Map.of() : variables);
        vars.putIfAbsent("title", title);
        vars.putIfAbsent("starterUserId", String.valueOf(starterUserId));
        vars.putIfAbsent("starterOrgId", String.valueOf(starterOrgId));

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(processKey, businessId, vars);
        return new StartedInstance(instance.getId(), instance.getProcessDefinitionId());
    }

    @Override
    public TaskInfo getTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return null;
        }
        return toInfo(task);
    }

    @Override
    public List<TaskInfo> listTasksByAssignee(Long userId) {
        return taskService.createTaskQuery().taskAssignee(String.valueOf(userId)).list().stream()
                .map(this::toInfo).collect(Collectors.toList());
    }

    @Override
    public List<TaskInfo> activeTasks(String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list().stream()
                .map(this::toInfo).collect(Collectors.toList());
    }

    @Override
    public void complete(String taskId) {
        taskService.complete(taskId);
    }

    @Override
    public void setRejected(String executionId, String variableName) {
        runtimeService.setVariable(executionId, variableName, true);
    }

    @Override
    public void setAssignee(String taskId, Long userId) {
        taskService.setAssignee(taskId, String.valueOf(userId));
    }

    @Override
    public void claim(String taskId, Long userId) {
        try {
            // Flowable 的 claim 仅对「无办理人」的任务合法；若已被他人认领会抛 FlowableTaskAlreadyClaimedException。
            // 业务层已对 assignee 做了预检，这里仍兜底转换，防止并发认领或引擎态与服务端缓存不一致导致 500。
            taskService.claim(taskId, String.valueOf(userId));
        } catch (FlowableTaskAlreadyClaimedException e) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "任务已被他人认领");
        }
    }

    @Override
    public String status(String processInstanceId) {
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (running != null) {
            return "RUNNING";
        }
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (historic == null) {
            return "UNKNOWN";
        }
        return historic.getEndTime() == null ? "RUNNING" : "FINISHED";
    }

    @Override
    public List<TaskInfo> overdueTasks(LocalDateTime before, int firstResult, int maxResults) {
        Date bound = toDate(before);
        return taskService.createTaskQuery()
                .taskDueBefore(bound)
                .orderByTaskDueDate().asc()
                .listPage(firstResult, maxResults).stream()
                .map(this::toInfo)
                .collect(Collectors.toList());
    }

    @Override
    public long countOverdueTasks(LocalDateTime before) {
        return taskService.createTaskQuery()
                .taskDueBefore(toDate(before))
                .count();
    }

    @Override
    public List<DefinitionVersion> definitionVersions(String processKey) {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .orderByProcessDefinitionVersion().desc()
                .list();
        if (definitions.isEmpty()) {
            return List.of();
        }
        // ProcessDefinition 不直接暴露部署时间，按 deploymentId 批量关联 Deployment（一次查询，避免 N+1）
        Map<String, Date> deployTimes = new HashMap<>();
        repositoryService.createDeploymentQuery()
                .deploymentIds(definitions.stream().map(ProcessDefinition::getDeploymentId).distinct().toList())
                .list()
                .forEach(d -> deployTimes.put(d.getId(), d.getDeploymentTime()));
        return definitions.stream()
                .map(pd -> new DefinitionVersion(pd.getId(), pd.getVersion(), pd.getName(), pd.getKey(),
                        pd.isSuspended(), pd.getDeploymentId(),
                        deployTimes.containsKey(pd.getDeploymentId())
                                ? toLocal(deployTimes.get(pd.getDeploymentId())) : null))
                .collect(Collectors.toList());
    }

    @Override
    public ProcessDefinitionState processDefinitionState(String processDefinitionId) {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId).singleResult();
        if (pd == null) {
            return null;
        }
        return new ProcessDefinitionState(pd.getId(), pd.getKey(), pd.getVersion(), pd.isSuspended(), pd.getName());
    }

    @Override
    public void suspendDefinition(String processDefinitionId) {
        // 仅挂起定义本身，不级联挂起在途实例：新实例无法发起，已运行的业务照常推进。
        repositoryService.suspendProcessDefinitionById(processDefinitionId);
    }

    @Override
    public void activateDefinition(String processDefinitionId) {
        repositoryService.activateProcessDefinitionById(processDefinitionId);
    }

    @Override
    public boolean isInstanceSuspended(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        return pi != null && pi.isSuspended();
    }

    @Override
    public void suspendInstance(String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
    }

    @Override
    public void resumeInstance(String processInstanceId) {
        runtimeService.activateProcessInstanceById(processInstanceId);
    }

    @Override
    public String processDefinitionIdOfInstance(String processInstanceId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        return pi == null ? null : pi.getProcessDefinitionId();
    }

    @Override
    public void migrateInstance(String processInstanceId, String toProcessDefinitionId) {
        ProcessMigrationService svc = processMigrationServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "当前引擎未启用流程迁移服务（ProcessMigrationService），无法执行实例迁移");
        }
        // 同流程不同版本热升级：按活动 id 自动映射（无需显式活动级映射）；migrate 以实例 id 直接执行。
        svc.createProcessInstanceMigrationBuilder()
                .migrateToProcessDefinition(toProcessDefinitionId)
                .migrate(processInstanceId);
    }

    private TaskInfo toInfo(Task task) {
        Long assignee = task.getAssignee() == null ? null : Long.valueOf(task.getAssignee());
        LocalDateTime dueAt = task.getDueDate() == null ? null : toLocal(task.getDueDate());
        return new TaskInfo(task.getId(), task.getExecutionId(), task.getProcessInstanceId(),
                task.getTaskDefinitionKey(), task.getName(), assignee, dueAt);
    }

    private LocalDateTime toLocal(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
