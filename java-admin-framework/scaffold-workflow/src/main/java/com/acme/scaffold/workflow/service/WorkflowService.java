package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.idempotency.Idempotent;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.monitor.domain.BusinessMetrics;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.system.user.domain.model.SysUserDO;
import com.acme.scaffold.workflow.dto.ApprovalRecordView;
import com.acme.scaffold.workflow.dto.CompleteTaskRequest;
import com.acme.scaffold.workflow.dto.DelegateTaskRequest;
import com.acme.scaffold.workflow.dto.InstanceDetailView;
import com.acme.scaffold.workflow.dto.InstanceView;
import com.acme.scaffold.workflow.dto.ResolveDelegateRequest;
import com.acme.scaffold.workflow.dto.StartProcessRequest;
import com.acme.scaffold.workflow.dto.TaskView;
import com.acme.scaffold.workflow.dto.TransferTaskRequest;
import com.acme.scaffold.workflow.entity.WfApprovalRecordDO;
import com.acme.scaffold.workflow.entity.WfInstanceExtDO;
import com.acme.scaffold.workflow.entity.WfTaskExtDO;
import com.acme.scaffold.workflow.model.ApprovalAction;
import com.acme.scaffold.workflow.dto.design.WorkflowDesignView;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import com.acme.scaffold.workflow.rules.ClaimRules;
import com.acme.scaffold.workflow.service.WorkflowDesignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作流应用服务：编排引擎端口与业务扩展表，保证审批记录不可变且写操作幂等。
 * 支持会签（ALL）、或签（ANY）、驳回、转办、委派（临时委托他人办理，办结交还原审批人）。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {
    private static final SqlTables.TableRef WF_APPROVAL_RECORD = SqlTables.WF_APPROVAL_RECORD;
    private static final SqlTables.TableRef WF_INSTANCE_EXT = SqlTables.WF_INSTANCE_EXT;
    private static final SqlTables.TableRef WF_TASK_EXT = SqlTables.WF_TASK_EXT;

    private static final SqlTables.TableRef USER = SqlTables.SYS_USER;

    private final WorkflowEnginePort engine;
    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final BusinessMetrics businessMetrics;
    private final WorkflowDesignService designService;
    private final RealtimePushService realtimePushService;

    @Transactional
    public String start(StartProcessRequest request) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        String approvalMode = request.approvalMode() == null ? "ALL" : request.approvalMode();

        // 路由：所有流程（内置 leaveApproval / expense 与自定义）均经 wf_workflow_design 的「已发布」设计驱动；
        // 内置流程现在都是 BuiltinWorkflowSeeder 种子写入并发布的可编辑设计，不再依赖 classpath 下的静态 BPMN。
        // 若某 processKey 无已发布设计（如用户仅保存草稿未发布），则退回原变量契约兜底（前端会显示审批人/主管选择框）。
        Map<String, Object> variables;
        WorkflowDesignView design = designService.getPublished(request.processKey());
        if (design != null) {
            variables = designService.buildStartVariables(design, principal.userId(), 0L, request.formFields());
        } else {
            variables = new HashMap<>(4);
            List<String> assignees = request.assigneeUserIds() == null ? List.of()
                    : request.assigneeUserIds().stream().map(String::valueOf).toList();
            variables.put("assigneeList", assignees);
            variables.put("manager", String.valueOf(request.managerUserId() == null ? 0L : request.managerUserId()));
            variables.put("approvalMode", approvalMode);
            variables.put("rejected", false);
            variables.put("managerApproved", true);
        }

        WorkflowEnginePort.StartedInstance instance = engine.start(request.processKey(), request.businessId(),
                request.title(), principal.userId(), 0L, variables);

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
        SqlWriters.insert(dsl, WF_INSTANCE_EXT, ext);

        List<WorkflowEnginePort.TaskInfo> activeTasks = engine.activeTasks(instance.processInstanceId());
        activeTasks.forEach(t -> saveTaskExt(instance.processInstanceId(), t));
        // 实时推送待办提醒给当前活跃任务的审批人；离线用户进入页面时通过 REST 拉取待办（保证至少一次可见）
        List<Long> todoAssignees = activeTasks.stream()
                .map(WorkflowEnginePort.TaskInfo::assigneeUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!todoAssignees.isEmpty()) {
            Map<String, Object> todoPayload = Map.of("businessId", request.businessId(), "title", request.title(),
                    "processKey", request.processKey(), "instanceId", instance.processInstanceId());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                // 事务提交后再推送：① 回滚时审批人不会收到「假待办」；② 不在持连接期间做网络 IO 拉长事务
                // （与 AuditLogService 的 afterCommit 模式一致）
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        realtimePushService.sendToUsers(todoAssignees, RealtimePushService.TYPE_TODO_REMINDER,
                                todoPayload);
                    }
                });
            } else {
                realtimePushService.sendToUsers(todoAssignees, RealtimePushService.TYPE_TODO_REMINDER, todoPayload);
            }
        }
        return instance.processInstanceId();
    }

    /**
     * 审批 / 驳回任务。
     *
     * <p>双重幂等：{@code operationId} 兜住「同一审批动作重复送达」（引擎层），
     * {@link Idempotent}（requireKey=true）兜住「请求头缺键 / 并发重复提交」——
     * 审批重复执行一次就是一次业务事故，故强制要求客户端带 {@code Idempotency-Key}。
     */
    @Idempotent(scope = "workflow:task:complete", requireKey = true)
    @Transactional
    public void completeTask(CompleteTaskRequest request) {
        // TRANSFER 属于「转办」而非审批动作：complete 只区分 REJECT/非 REJECT，若放任其直达
        // 会被静默当作一次普通通过（任务被办结却不产生转办记录），属于业务语义事故，故显式拦截。
        if (request.action() == ApprovalAction.TRANSFER) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "TRANSFER 不是审批动作，请调用转办接口 /api/workflow/tasks/transfer");
        }
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
            // 内置流程读取 rejected；自定义流程读取 rejected_<节点id>，一并置位以兼容两类设计
            engine.setRejected(task.executionId(), "rejected");
            engine.setRejected(task.executionId(), "rejected_" + task.activityId());
        }
        engine.complete(task.taskId());
        businessMetrics.recordApproval(System.currentTimeMillis() - begin);

        markTaskCompleted(task.taskId());
        saveApprovalRecord(request.operationId(), task, request.action().name(),
                principal.userId(), null, null, request.opinion(), null);
        refreshInstanceStatus(task.processInstanceId());
    }

    /** 转办任务：同样强制幂等键，避免重复转办把任务来回搬移。 */
    @Idempotent(scope = "workflow:task:transfer", requireKey = true)
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

        dsl.update(WF_TASK_EXT.table())
                .set(WF_TASK_EXT.field("assignee_user_id", Long.class), request.toUserId())
                .where(WF_TASK_EXT.field("task_id", String.class).eq(task.taskId()))
                .execute();
        saveApprovalRecord(request.operationId(), task, "TRANSFER",
                principal.userId(), fromUserId, request.toUserId(), request.opinion(), null);
    }

    /**
     * 委派任务：把自己当前待办临时委托给 {@code toUserId} 办理，原审批人保留所有权。
     *
     * <p>双重幂等（同 {@link #transfer}）：{@code operationId} 兜住重复送达，{@link Idempotent}（requireKey=true）
     * 兜住缺键/并发重复提交。委派后引擎将该任务 assignee 置为被委托人、{@code delegationState=PENDING}；
     * 被委托人办结（{@link #resolveDelegate}）后任务回到原审批人（{@code delegationState=RESOLVED}），由其最终完成。
     */
    @Idempotent(scope = "workflow:task:delegate", requireKey = true)
    @Transactional
    public void delegate(DelegateTaskRequest request) {
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
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能委派自己待办的任务");
        }
        if ("PENDING".equals(task.delegationState())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "任务已处于委派待办状态，不能重复委派");
        }
        engine.delegate(task.taskId(), request.toUserId());

        dsl.update(WF_TASK_EXT.table())
                .set(WF_TASK_EXT.field("assignee_user_id", Long.class), request.toUserId())
                .set(WF_TASK_EXT.field("delegator_user_id", Long.class), fromUserId)
                .set(WF_TASK_EXT.field("delegation_state", String.class), "PENDING")
                .where(WF_TASK_EXT.field("task_id", String.class).eq(task.taskId()))
                .execute();
        saveApprovalRecord(request.operationId(), task, "DELEGATE",
                principal.userId(), fromUserId, request.toUserId(), request.opinion(), null);
    }

    /**
     * 办结委派：被委托人完成办理后将任务交还原审批人。
     *
     * <p>仅当当前用户为被委托人（assignee 即该用户且 {@code delegationState=PENDING}）可调；
     * 调用引擎 {@code resolve} 将任务交还原审批人（delegationState→RESOLVED、assignee 还原），
     * 并记录一条 {@code DELEGATE_RESOLVE} 不可变审批记录。</p>
     */
    @Idempotent(scope = "workflow:task:resolve-delegate", requireKey = true)
    @Transactional
    public void resolveDelegate(ResolveDelegateRequest request) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        if (existsOperation(request.operationId())) {
            return;
        }
        WorkflowEnginePort.TaskInfo task = engine.getTask(request.taskId());
        if (task == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "任务不存在或已完成");
        }
        if (task.assigneeUserId() == null || !task.assigneeUserId().equals(principal.userId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "当前用户不是该委派的被委托人");
        }
        if (!"PENDING".equals(task.delegationState())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "该任务未处于委派待办状态");
        }
        engine.resolve(task.taskId());

        WfTaskExtDO ext = SqlWriters.fetchOne(dsl, WF_TASK_EXT, WfTaskExtDO.class,
                WF_TASK_EXT.field("task_id", String.class).eq(task.taskId()));
        Long delegator = ext == null ? null : ext.getDelegatorUserId();
        dsl.update(WF_TASK_EXT.table())
                .set(WF_TASK_EXT.field("assignee_user_id", Long.class),
                        delegator == null ? task.assigneeUserId() : delegator)
                .set(WF_TASK_EXT.field("delegation_state", String.class), "RESOLVED")
                .where(WF_TASK_EXT.field("task_id", String.class).eq(task.taskId()))
                .execute();
        saveApprovalRecord(request.operationId(), task, "DELEGATE_RESOLVE",
                principal.userId(), principal.userId(), delegator, request.opinion(), null);
    }

    /**
     * 认领任务：仅适用于尚未指定办理人（assignee 为空）的池化任务。
     *
     * <p>规则：任务不存在 → 404；已被他人认领 → 409（不可抢占）；
     * 已是自己认领 → 幂等成功（重复认领无副作用）；无办理人 → 调引擎 claim 并回写扩展表。
     * 认领动作天然幂等，故不强制 {@code Idempotency-Key}（与审批/转办不同，重复认领不会产生业务副作用）。
     */
    @Transactional
    public void claim(String taskId) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        WorkflowEnginePort.TaskInfo task = engine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "任务不存在或已完成");
        }
        ClaimRules.Decision decision = ClaimRules.decide(task.assigneeUserId(), principal.userId());
        switch (decision) {
            case TAKEN_BY_OTHER -> throw new BusinessException(CommonErrorCode.CONFLICT, "任务已被他人认领");
            case ALREADY_MINE -> {
                // 幂等：无需任何操作，直接返回成功
                return;
            }
            case CLAIMABLE -> {
                engine.claim(taskId, principal.userId());
                dsl.update(WF_TASK_EXT.table())
                        .set(WF_TASK_EXT.field("assignee_user_id", Long.class), principal.userId())
                        .where(WF_TASK_EXT.field("task_id", String.class).eq(taskId))
                        .execute();
            }
            default -> throw new IllegalStateException("未覆盖的认领判定: " + decision);
        }
    }

    public List<TaskView> myTasks() {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        List<WorkflowEnginePort.TaskInfo> tasks = engine.listTasksByAssignee(principal.userId());
        Map<String, Long> delegatorByTask = loadDelegators(
                tasks.stream().map(WorkflowEnginePort.TaskInfo::taskId).toList());
        return tasks.stream()
                .map(t -> new TaskView(t.taskId(), t.processInstanceId(), t.activityId(), t.name(),
                        "PENDING", t.assigneeUserId(), t.dueAt(),
                        delegatorByTask.get(t.taskId()), t.delegationState()))
                .collect(Collectors.toList());
    }

    /**
     * 已办列表：返回当前用户作为「原审批人」已办结（status=COMPLETED）的任务，
     * 按办结时间倒序。引擎端口仅暴露活跃任务，故已办须自查业务扩展表。
     * 活动名取自创建期快照 {@code activity_name}，无需回查引擎。
     */
    public List<TaskView> myDoneTasks() {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        Long userId = principal.userId();
        List<WfTaskExtDO> done = SqlWriters.fetchList(dsl, WF_TASK_EXT, WfTaskExtDO.class,
                WF_TASK_EXT.field("status", String.class).eq("COMPLETED")
                        .and(WF_TASK_EXT.field("original_assignee_user_id", Long.class).eq(userId)),
                WF_TASK_EXT.field("completed_at", LocalDateTime.class).desc());
        Map<String, Long> delegatorByTask = loadDelegators(
                done.stream().map(WfTaskExtDO::getTaskId).toList());
        return done.stream()
                .map(e -> new TaskView(e.getTaskId(), e.getProcessInstanceId(), e.getActivityId(),
                        e.getActivityName(), "COMPLETED", e.getAssigneeUserId(), e.getCompletedAt(),
                        delegatorByTask.get(e.getTaskId()), e.getDelegationState()))
                .collect(Collectors.toList());
    }

    public List<InstanceView> myInstances() {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        return SqlWriters.fetchList(dsl, WF_INSTANCE_EXT, WfInstanceExtDO.class,
                        WF_INSTANCE_EXT.field("starter_user_id", Long.class).eq(principal.userId()),
                        WF_INSTANCE_EXT.field("started_at", LocalDateTime.class).desc())
                .stream().map(this::toInstanceView).collect(Collectors.toList());
    }

    public List<ApprovalRecordView> records(String processInstanceId) {        return SqlWriters.fetchList(dsl, WF_APPROVAL_RECORD, WfApprovalRecordDO.class,
                        WF_APPROVAL_RECORD.field("process_instance_id", String.class)
                                .eq(processInstanceId),
                        WF_APPROVAL_RECORD.field("occurred_at", LocalDateTime.class).asc())
                .stream().map(r -> new ApprovalRecordView(r.getOperationId(), r.getAction(), r.getOperatorUserId(),
                        r.getFromUserId(), r.getToUserId(), r.getOpinion(), r.getOccurredAt()))
                .collect(Collectors.toList());
    }

    /**
     * 实例详情：聚合实例基础信息、发起用户姓名、当前活跃任务与历史审批记录。
     * 供「流程详情」页一次拉取，避免多次往返。
     */
    public InstanceDetailView detail(String processInstanceId) {
        WfInstanceExtDO ext = SqlWriters.fetchOne(dsl, WF_INSTANCE_EXT, WfInstanceExtDO.class,
                WF_INSTANCE_EXT.field("process_instance_id", String.class).eq(processInstanceId));
        if (ext == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "流程实例不存在");
        }
        String starterUsername = resolveUsername(ext.getStarterUserId());
        List<WorkflowEnginePort.TaskInfo> active = engine.activeTasks(processInstanceId);
        Map<String, Long> delegatorByTask = loadDelegators(
                active.stream().map(WorkflowEnginePort.TaskInfo::taskId).toList());
        List<TaskView> currentTasks = active.stream()
                .map(t -> new TaskView(t.taskId(), t.processInstanceId(), t.activityId(), t.name(),
                        "PENDING", t.assigneeUserId(), t.dueAt(),
                        delegatorByTask.get(t.taskId()), t.delegationState()))
                .collect(Collectors.toList());
        List<ApprovalRecordView> records = records(processInstanceId);
        return new InstanceDetailView(ext.getProcessInstanceId(), ext.getBusinessType(), ext.getBusinessId(),
                ext.getTitle(), ext.getStatus(), ext.getStartedAt(), ext.getFinishedAt(),
                ext.getStarterUserId(), starterUsername, ext.getCurrentActivityId(), currentTasks, records);
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUserDO user = SqlWriters.fetchById(dsl, USER, SysUserDO.class, userId);
        return user == null ? null : user.getUsername();
    }

    private boolean existsOperation(String operationId) {
        return SqlWriters.count(dsl, WF_APPROVAL_RECORD,
                WF_APPROVAL_RECORD.field("operation_id", String.class).eq(operationId)) > 0;
    }

    /**
     * 批量加载任务的委派发起人（原审批人），避免待办列表 N+1 查询。
     * 非委派任务或无扩展行时返回空映射，对应 {@code delegatorUserId} 为 null。
     */
    private Map<String, Long> loadDelegators(List<String> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return SqlWriters.fetchList(dsl, WF_TASK_EXT, WfTaskExtDO.class,
                        WF_TASK_EXT.field("task_id", String.class).in(taskIds))
                .stream().filter(e -> e.getDelegatorUserId() != null)
                .collect(Collectors.toMap(WfTaskExtDO::getTaskId, WfTaskExtDO::getDelegatorUserId,
                        (a, b) -> a));
    }

    private void saveTaskExt(String processInstanceId, WorkflowEnginePort.TaskInfo t) {
        WfTaskExtDO ext = new WfTaskExtDO();
        ext.setTaskId(t.taskId());
        ext.setProcessInstanceId(processInstanceId);
        ext.setActivityId(t.activityId());
        ext.setActivityName(t.name());
        ext.setAssigneeUserId(t.assigneeUserId());
        ext.setOriginalAssigneeUserId(t.assigneeUserId());
        ext.setStatus("PENDING");
        ext.setApprovalMode("ANY");
        ext.setDueAt(t.dueAt());
        SqlWriters.insert(dsl, WF_TASK_EXT, ext);
    }

    private void markTaskCompleted(String taskId) {
        dsl.update(WF_TASK_EXT.table())
                .set(WF_TASK_EXT.field("status", String.class), "COMPLETED")
                .set(WF_TASK_EXT.field("completed_at", Timestamp.class),
                        Timestamp.valueOf(LocalDateTime.now()))
                .where(WF_TASK_EXT.field("task_id", String.class).eq(taskId))
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
        SqlWriters.insert(dsl, WF_APPROVAL_RECORD, record);
    }

    private void refreshInstanceStatus(String processInstanceId) {
        WfInstanceExtDO ext = SqlWriters.fetchOne(dsl, WF_INSTANCE_EXT, WfInstanceExtDO.class,
                WF_INSTANCE_EXT.field("process_instance_id", String.class).eq(processInstanceId));
        if (ext == null) {
            return;
        }
        String engineStatus = engine.status(processInstanceId);
        if ("FINISHED".equals(engineStatus)) {
            boolean rejected = SqlWriters.count(dsl, WF_APPROVAL_RECORD,
                    DSL.and(WF_APPROVAL_RECORD.field("process_instance_id", String.class)
                                    .eq(processInstanceId),
                            WF_APPROVAL_RECORD.field("action", String.class).eq("REJECT"))) > 0;
            dsl.update(WF_INSTANCE_EXT.table())
                    .set(WF_INSTANCE_EXT.field("status", String.class),
                            rejected ? "REJECTED" : "APPROVED")
                    .set(WF_INSTANCE_EXT.field("finished_at", Timestamp.class),
                            Timestamp.valueOf(LocalDateTime.now()))
                    .where(WF_INSTANCE_EXT.field("process_instance_id", String.class)
                            .eq(processInstanceId))
                    .execute();
        } else {
            dsl.update(WF_INSTANCE_EXT.table())
                    .set(WF_INSTANCE_EXT.field("current_activity_id", String.class),
                            currentActivity(processInstanceId))
                    .where(WF_INSTANCE_EXT.field("process_instance_id", String.class)
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
