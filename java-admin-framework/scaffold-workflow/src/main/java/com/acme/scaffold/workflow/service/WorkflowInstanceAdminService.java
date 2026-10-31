package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.workflow.entity.WfApprovalRecordDO;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import com.acme.scaffold.workflow.rules.WorkflowInstanceAdminRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程实例治理服务（M23 续）：管理员视角的实例高风险写操作——重新指派、挂起/恢复、跨版本迁移。
 *
 * <p><b>与 {@code WorkflowService} 个人面区分</b>：个人面 {@code transfer}/{@code claim} 要求操作人即当前
 * 办理人（自我保护）；本服务面向管理员，可越过当前办理人将任务改派他人、整体挂起/恢复实例或热升级定义版本，
 * 故守卫更严格：实例不存在→404、已结束→409、状态冲突→409、目标非法→400。</p>
 *
 * <p><b>写操作均落 {@code wf_approval_record} 审计</b>（action=REASSIGN/SUSPEND/RESUME/MIGRATE，零新表），
 * 重新指派/迁移同步回写扩展表，保证监控面与引擎态一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowInstanceAdminService {
    private static final JooqTables.TableRef WF_APPROVAL_RECORD = JooqTables.WF_APPROVAL_RECORD;
    private static final JooqTables.TableRef WF_INSTANCE_EXT = JooqTables.WF_INSTANCE_EXT;
    private static final JooqTables.TableRef WF_TASK_EXT = JooqTables.WF_TASK_EXT;


    private final WorkflowEnginePort engine;
    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final RealtimePushService realtimePushService;

    /**
     * 重新指派实例的全部活跃任务到目标办理人。
     *
     * <p>守卫：目标为空 → 400；实例不存在 → 404；已结束 → 409；无活跃任务 → 409。
     * 逐任务 {@code setAssignee} + 回写 {@code wf_task_ext}，并落 REASSIGN 审计；
     * 事务提交后向新办理人推送待办提醒（回滚则不会误推）。</p>
     */
    @Transactional
    public void reassign(String processInstanceId, Long toUserId) {
        if (toUserId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "目标办理人不能为空");
        }
        assertInstanceExists(processInstanceId);
        List<WorkflowEnginePort.TaskInfo> tasks = engine.activeTasks(processInstanceId);
        if (tasks.isEmpty()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "实例当前无活跃任务可重新指派");
        }
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        for (WorkflowEnginePort.TaskInfo task : tasks) {
            engine.setAssignee(task.taskId(), toUserId);
            dsl.update(WF_TASK_EXT.table())
                    .set(WF_TASK_EXT.field("assignee_user_id", Long.class), toUserId)
                    .where(WF_TASK_EXT.field("task_id", String.class).eq(task.taskId()))
                    .execute();
            saveAdminRecord(processInstanceId, task.taskId(), task.activityId(),
                    "REASSIGN", principal.userId(), task.assigneeUserId(), toUserId, "管理员重新指派");
        }
        Long finalTo = toUserId;
        String piId = processInstanceId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    realtimePushService.sendToUsers(List.of(finalTo), RealtimePushService.TYPE_TODO_REMINDER,
                            Map.of("processInstanceId", piId, "instanceId", piId,
                                    "title", "您有新的待办（已被重新指派）"));
                }
            });
        } else {
            realtimePushService.sendToUsers(List.of(finalTo), RealtimePushService.TYPE_TODO_REMINDER,
                    Map.of("processInstanceId", piId, "instanceId", piId,
                            "title", "您有新的待办（已被重新指派）"));
        }
        log.info("实例重新指派 processInstanceId={} toUserId={} 任务数={} 操作人={}",
                processInstanceId, toUserId, tasks.size(), principal.userId());
    }

    /**
     * 挂起流程实例：冻结在途任务（引擎态 suspended），待办不再推进。
     *
     * <p>守卫：实例不存在 → 404；已结束 → 409；已挂起 → 409（幂等拒绝）。</p>
     */
    @Transactional
    public void suspend(String processInstanceId) {
        assertInstanceExists(processInstanceId);
        WorkflowInstanceAdminRules.InstanceStateTransition transition =
                WorkflowInstanceAdminRules.evaluateSuspend(engine.isInstanceSuspended(processInstanceId));
        switch (transition) {
            case ALREADY_SUSPENDED ->
                    throw new BusinessException(CommonErrorCode.CONFLICT, "流程实例已处于挂起状态");
            case OK -> {
                engine.suspendInstance(processInstanceId);
                recordInstanceAction(processInstanceId, "SUSPEND");
                log.info("实例挂起 processInstanceId={} 操作人={}", processInstanceId,
                        securityContextFacade.requireCurrentPrincipal().userId());
            }
            default -> throw new IllegalStateException("未覆盖的实例状态转移: " + transition);
        }
    }

    /**
     * 恢复（取消挂起）流程实例。
     *
     * <p>守卫：实例不存在 → 404；已结束 → 409；未挂起 → 409（幂等拒绝）。</p>
     */
    @Transactional
    public void resume(String processInstanceId) {
        assertInstanceExists(processInstanceId);
        WorkflowInstanceAdminRules.InstanceStateTransition transition =
                WorkflowInstanceAdminRules.evaluateResume(engine.isInstanceSuspended(processInstanceId));
        switch (transition) {
            case NOT_SUSPENDED ->
                    throw new BusinessException(CommonErrorCode.CONFLICT, "流程实例未处于挂起状态");
            case OK -> {
                engine.resumeInstance(processInstanceId);
                recordInstanceAction(processInstanceId, "RESUME");
                log.info("实例恢复 processInstanceId={} 操作人={}", processInstanceId,
                        securityContextFacade.requireCurrentPrincipal().userId());
            }
            default -> throw new IllegalStateException("未覆盖的实例状态转移: " + transition);
        }
    }

    /**
     * 将运行实例迁移到目标流程定义版本（同流程不同版本热升级）。
     *
     * <p>守卫：实例不存在 → 404；已结束 → 409；目标为空 → 400；目标与当前版本一致 → 400（无意义）；
     * 目标定义不存在 → 404。迁移成功后回写 {@code wf_instance_ext.process_definition_id} 并落 MIGRATE 审计。</p>
     */
    @Transactional
    public void migrate(String processInstanceId, String toProcessDefinitionId) {
        if (toProcessDefinitionId == null || toProcessDefinitionId.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "目标流程定义不能为空");
        }
        assertInstanceExists(processInstanceId);
        String currentDefId = engine.processDefinitionIdOfInstance(processInstanceId);
        if (currentDefId != null && currentDefId.equals(toProcessDefinitionId)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "目标流程定义与当前一致，无需迁移");
        }
        WorkflowEnginePort.ProcessDefinitionState target = engine.processDefinitionState(toProcessDefinitionId);
        if (target == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "目标流程定义不存在: " + toProcessDefinitionId);
        }
        engine.migrateInstance(processInstanceId, toProcessDefinitionId);
        dsl.update(WF_INSTANCE_EXT.table())
                .set(WF_INSTANCE_EXT.field("process_definition_id", String.class), toProcessDefinitionId)
                .where(WF_INSTANCE_EXT.field("process_instance_id", String.class).eq(processInstanceId))
                .execute();
        recordInstanceAction(processInstanceId, "MIGRATE", "to=" + toProcessDefinitionId);
        log.info("实例迁移 processInstanceId={} from={} to={} 操作人={}", processInstanceId, currentDefId,
                toProcessDefinitionId, securityContextFacade.requireCurrentPrincipal().userId());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    /** 实例必须存在且处于运行中（非结束）才允许治理写操作；否则 404/409。 */
    private void assertInstanceExists(String processInstanceId) {
        String status = engine.status(processInstanceId);
        if ("UNKNOWN".equals(status)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "流程实例不存在: " + processInstanceId);
        }
        if (!"RUNNING".equals(status)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "流程实例已结束，无法操作");
        }
    }

    private void recordInstanceAction(String processInstanceId, String action) {
        recordInstanceAction(processInstanceId, action, null);
    }

    private void recordInstanceAction(String processInstanceId, String action, String opinion) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        saveAdminRecord(processInstanceId, null, null, action, principal.userId(), null, null, opinion);
    }

    private void saveAdminRecord(String processInstanceId, String taskId, String activityId, String action,
                                 Long operatorUserId, Long fromUserId, Long toUserId, String opinion) {
        WfApprovalRecordDO record = new WfApprovalRecordDO();
        record.setOperationId(action.toLowerCase() + "-" + processInstanceId + "-" + System.currentTimeMillis());
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setActivityId(activityId);
        record.setAction(action);
        record.setOperatorUserId(operatorUserId);
        record.setFromUserId(fromUserId);
        record.setToUserId(toUserId);
        record.setOpinion(opinion);
        record.setTraceId(org.slf4j.MDC.get("traceId"));
        record.setOccurredAt(LocalDateTime.now());
        JooqWriters.insert(dsl, WF_APPROVAL_RECORD, record);
    }
}
