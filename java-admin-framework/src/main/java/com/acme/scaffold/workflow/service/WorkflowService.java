package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.monitor.BusinessMetrics;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.workflow.dto.ApprovalRecordView;
import com.acme.scaffold.workflow.dto.CompleteTaskRequest;
import com.acme.scaffold.workflow.dto.InstanceView;
import com.acme.scaffold.workflow.dto.StartProcessRequest;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.dto.TransferTaskRequest;
import com.acme.scaffold.workflow.entity.WfApprovalRecordDO;
import com.acme.scaffold.workflow.entity.WfInstanceExtDO;
import com.acme.scaffold.workflow.entity.WfTaskExtDO;
import com.acme.scaffold.workflow.model.ApprovalAction;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流应用服务：编排引擎端口与业务扩展表，保证审批记录不可变且写操作幂等。
 * 支持会签（ALL）、或签（ANY）、驳回、转办。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowEnginePort engine;
    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final BusinessMetrics businessMetrics;

    @Transactional
    public String start(StartProcessRequest request) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        String approvalMode = request.approvalMode() == null ? "ALL" : request.approvalMode();
        WorkflowEnginePort.StartedInstance instance = engine.start(request.processKey(), request.businessId(),
                request.title(), principal.userId(), 0L, request.assigneeUserIds(),
                request.managerUserId(), approvalMode);

        WfInstanceExtDO ext = new WfInstanceExtDO();
        ext.setProcessInstanceId(instance.processInstanceId());
        ext.setProcessDefinitionId(instance.processDefinitionId());
        ext.setBusinessType(request.businessType());
        ext.setBusinessId(request.businessId());
        ext.setTitle(request.title());
        ext.setStarterUserId(principal.userId());
        ext.setStarterOrgId(0L);
        ext.setStatus("RUNNING");
        ext.setStartedAt(LocalDateTime.now());
        JooqWriters.insert(dsl, JooqTables.WF_INSTANCE_EXT, ext);

        engine.activeTasks(instance.processInstanceId())
                .forEach(t -> saveTaskExt(instance.processInstanceId(), t));
        return instance.processInstanceId();
    }

    @Transactional
    public void completeTask(CompleteTaskRequest request) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        if (existsOperation(request.operationId())) {
            log.info("审批操作幂等命中 operationId={}", request.operationId());
            return;
        }
        WorkflowEnginePort.TaskInfo task = engine.getTask(request.taskId());
        if (task == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "任务不存在或已完成");
        }
        if (task.assigneeUserId() == null || !task.assigneeUserId().equals(principal.userId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "当前用户不是该任务的审批人");
        }

        long begin = System.currentTimeMillis();
        if (request.action() == ApprovalAction.REJECT) {
            engine.setRejected(task.executionId());
        }
        engine.complete(task.taskId());
        businessMetrics.recordApproval(System.currentTimeMillis() - begin);

        markTaskCompleted(task.taskId());
        saveApprovalRecord(request.operationId(), task, request.action().name(),
                principal.userId(), null, null, request.opinion(), null);
        refreshInstanceStatus(task.processInstanceId());
    }

    @Transactional
    public void transfer(TransferTaskRequest request) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        if (existsOperation(request.operationId())) {
            return;
        }
        WorkflowEnginePort.TaskInfo task = engine.getTask(request.taskId());
        if (task == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "任务不存在或已完成");
        }
        Long fromUserId = task.assigneeUserId();
        if (fromUserId == null || !fromUserId.equals(principal.userId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能转办自己待办的任务");
        }
        engine.setAssignee(task.taskId(), request.toUserId());

        dsl.update(JooqTables.WF_TASK_EXT.table())
                .set(JooqTables.WF_TASK_EXT.field("assignee_user_id", Long.class), request.toUserId())
                .where(JooqTables.WF_TASK_EXT.field("task_id", String.class).eq(task.taskId()))
                .execute();
        saveApprovalRecord(request.operationId(), task, "TRANSFER",
                principal.userId(), fromUserId, request.toUserId(), request.opinion(), null);
    }

    public List<TaskView> myTasks() {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        return engine.listTasksByAssignee(principal.userId()).stream()
                .map(t -> new TaskView(t.taskId(), t.processInstanceId(), t.activityId(), t.name(),
                        "PENDING", t.assigneeUserId(), t.dueAt()))
                .collect(Collectors.toList());
    }

    public List<InstanceView> myInstances() {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        return JooqWriters.fetchList(dsl, JooqTables.WF_INSTANCE_EXT, WfInstanceExtDO.class,
                        JooqTables.WF_INSTANCE_EXT.field("starter_user_id", Long.class).eq(principal.userId()),
                        JooqTables.WF_INSTANCE_EXT.field("started_at", LocalDateTime.class).desc())
                .stream().map(this::toInstanceView).collect(Collectors.toList());
    }

    public List<ApprovalRecordView> records(String processInstanceId) {
        return JooqWriters.fetchList(dsl, JooqTables.WF_APPROVAL_RECORD, WfApprovalRecordDO.class,
                        JooqTables.WF_APPROVAL_RECORD.field("process_instance_id", String.class)
                                .eq(processInstanceId),
                        JooqTables.WF_APPROVAL_RECORD.field("occurred_at", LocalDateTime.class).asc())
                .stream().map(r -> new ApprovalRecordView(r.getOperationId(), r.getAction(), r.getOperatorUserId(),
                        r.getFromUserId(), r.getToUserId(), r.getOpinion(), r.getOccurredAt()))
                .collect(Collectors.toList());
    }

    private boolean existsOperation(String operationId) {
        return JooqWriters.count(dsl, JooqTables.WF_APPROVAL_RECORD,
                JooqTables.WF_APPROVAL_RECORD.field("operation_id", String.class).eq(operationId)) > 0;
    }

    private void saveTaskExt(String processInstanceId, WorkflowEnginePort.TaskInfo t) {
        WfTaskExtDO ext = new WfTaskExtDO();
        ext.setTaskId(t.taskId());
        ext.setProcessInstanceId(processInstanceId);
        ext.setActivityId(t.activityId());
        ext.setAssigneeUserId(t.assigneeUserId());
        ext.setOriginalAssigneeUserId(t.assigneeUserId());
        ext.setStatus("PENDING");
        ext.setApprovalMode("ANY");
        ext.setDueAt(t.dueAt());
        JooqWriters.insert(dsl, JooqTables.WF_TASK_EXT, ext);
    }

    private void markTaskCompleted(String taskId) {
        dsl.update(JooqTables.WF_TASK_EXT.table())
                .set(JooqTables.WF_TASK_EXT.field("status", String.class), "COMPLETED")
                .set(JooqTables.WF_TASK_EXT.field("completed_at", Timestamp.class),
                        Timestamp.valueOf(LocalDateTime.now()))
                .where(JooqTables.WF_TASK_EXT.field("task_id", String.class).eq(taskId))
                .execute();
    }

    private void saveApprovalRecord(String operationId, WorkflowEnginePort.TaskInfo task, String action,
                                    Long operatorId, Long fromId, Long toId, String opinion, String snapshot) {
        WfApprovalRecordDO record = new WfApprovalRecordDO();
        record.setOperationId(operationId);
        record.setProcessInstanceId(task.processInstanceId());
        record.setTaskId(task.taskId());
        record.setActivityId(task.activityId());
        record.setAction(action);
        record.setOperatorUserId(operatorId);
        record.setFromUserId(fromId);
        record.setToUserId(toId);
        record.setOpinion(opinion);
        record.setSnapshot(snapshot);
        record.setTraceId(org.slf4j.MDC.get("traceId"));
        record.setOccurredAt(LocalDateTime.now());
        JooqWriters.insert(dsl, JooqTables.WF_APPROVAL_RECORD, record);
    }

    private void refreshInstanceStatus(String processInstanceId) {
        WfInstanceExtDO ext = JooqWriters.fetchOne(dsl, JooqTables.WF_INSTANCE_EXT, WfInstanceExtDO.class,
                JooqTables.WF_INSTANCE_EXT.field("process_instance_id", String.class).eq(processInstanceId));
        if (ext == null) {
            return;
        }
        String engineStatus = engine.status(processInstanceId);
        if ("FINISHED".equals(engineStatus)) {
            boolean rejected = JooqWriters.count(dsl, JooqTables.WF_APPROVAL_RECORD,
                    DSL.and(JooqTables.WF_APPROVAL_RECORD.field("process_instance_id", String.class)
                                    .eq(processInstanceId),
                            JooqTables.WF_APPROVAL_RECORD.field("action", String.class).eq("REJECT"))) > 0;
            dsl.update(JooqTables.WF_INSTANCE_EXT.table())
                    .set(JooqTables.WF_INSTANCE_EXT.field("status", String.class),
                            rejected ? "REJECTED" : "APPROVED")
                    .set(JooqTables.WF_INSTANCE_EXT.field("finished_at", Timestamp.class),
                            Timestamp.valueOf(LocalDateTime.now()))
                    .where(JooqTables.WF_INSTANCE_EXT.field("process_instance_id", String.class)
                            .eq(processInstanceId))
                    .execute();
        } else {
            dsl.update(JooqTables.WF_INSTANCE_EXT.table())
                    .set(JooqTables.WF_INSTANCE_EXT.field("current_activity_id", String.class),
                            currentActivity(processInstanceId))
                    .where(JooqTables.WF_INSTANCE_EXT.field("process_instance_id", String.class)
                            .eq(processInstanceId))
                    .execute();
        }
    }

    private String currentActivity(String processInstanceId) {
        return engine.activeTasks(processInstanceId).stream().findFirst()
                .map(WorkflowEnginePort.TaskInfo::activityId).orElse(null);
    }

    private InstanceView toInstanceView(WfInstanceExtDO e) {
        return new InstanceView(e.getProcessInstanceId(), e.getBusinessType(), e.getBusinessId(), e.getTitle(),
                e.getStatus(), e.getStartedAt(), e.getFinishedAt());
    }
}
