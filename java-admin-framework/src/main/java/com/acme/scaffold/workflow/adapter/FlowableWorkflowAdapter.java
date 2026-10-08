package com.acme.scaffold.workflow.adapter;

import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
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

    public FlowableWorkflowAdapter(RuntimeService runtimeService, TaskService taskService,
                                   RepositoryService repositoryService, HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
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

    private TaskInfo toInfo(Task task) {
        Long assignee = task.getAssignee() == null ? null : Long.valueOf(task.getAssignee());
        LocalDateTime dueAt = task.getDueDate() == null ? null : toLocal(task.getDueDate());
        return new TaskInfo(task.getId(), task.getExecutionId(), task.getProcessInstanceId(),
                task.getTaskDefinitionKey(), task.getName(), assignee, dueAt);
    }

    private LocalDateTime toLocal(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
