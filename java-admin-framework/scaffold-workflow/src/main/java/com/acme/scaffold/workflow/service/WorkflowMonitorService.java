package com.acme.scaffold.workflow.service;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.realtime.service.RealtimePushService;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.workflow.config.WorkflowMonitorProperties;
import com.acme.scaffold.workflow.dto.DefinitionVersionView;
import com.acme.scaffold.workflow.dto.MonitorStatsView;
import com.acme.scaffold.workflow.dto.OverdueTaskView;
import com.acme.scaffold.workflow.dto.RunningInstanceView;
import com.acme.scaffold.workflow.entity.WfApprovalRecordDO;
import com.acme.scaffold.workflow.entity.WfInstanceExtDO;
import com.acme.scaffold.workflow.entity.WfTaskExtDO;
import com.acme.scaffold.workflow.port.WorkflowEnginePort;
import com.acme.scaffold.workflow.rules.WorkflowDefinitionRules;
import com.acme.scaffold.workflow.rules.WorkflowMonitorRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.acme.scaffold.jooqgen.Tables;

/**
 * 流程监控应用服务（M23，M4 续）：在既有「我发起的 / 我的待办」个人面之外补管理员全局视角。
 *
 * <p><b>读面</b>（零副作用）：运行中实例分页（停留时长/当前节点）、超时任务分页
 * （引擎 {@code taskDueBefore} 视角 + 扩展表实例标题）、流程定义版本链、统计摘要。</p>
 *
 * <p><b>写面</b>（仅催办）：向超时/滞留任务的当前办理人推送实时提醒
 * （{@link RealtimePushService#TYPE_TODO_REMINDER}），受 {@link WorkflowMonitorProperties}
 * 的最小间隔约束防消息轰炸；催办记录落 {@code wf_approval_record}（action=REMIND），
 * 零新表，同时作为间隔判定依据。</p>
 *
 * <p><b>不做什么</b>：不提供流程定义的挂起/激活、实例的删除/迁移等写操作——
 * 这些动作直接影响在途业务，风险高，留待显式立项后再评估。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowMonitorService {
    private static final JooqTables.TableRef WF_APPROVAL_RECORD = JooqTables.toTableRef(Tables.WF_APPROVAL_RECORD);
    private static final JooqTables.TableRef WF_INSTANCE_EXT = JooqTables.toTableRef(Tables.WF_INSTANCE_EXT);
    private static final JooqTables.TableRef WF_TASK_EXT = JooqTables.toTableRef(Tables.WF_TASK_EXT);

    private static final JooqTables.TableRef USER = JooqTables.toTableRef(Tables.SYS_USER);

    /** 排序白名单：客户端字段名 → 数据库列名（白名单外忽略，防 ORDER BY 注入）。 */
    private static final Map<String, String> INSTANCE_SORT_FIELDS = JooqSorts.whitelist(
            "startedAt", "started_at",
            "title", "title",
            "status", "status",
            "stayedMinutes", "started_at",
            "id", "id");

    /** 催办记录动作标识（复用审批记录表，零新表；同时作为催办间隔的时间依据）。 */
    private static final String ACTION_REMIND = "REMIND";

    private final WorkflowEnginePort engine;
    private final DSLContext dsl;
    private final SecurityContextFacade securityContextFacade;
    private final WorkflowMonitorProperties properties;
    private final RealtimePushService realtimePushService;

    // ------------------------------------------------------------------
    // 读面：运行中实例
    // ------------------------------------------------------------------

    /**
     * 分页查询流程实例（默认 RUNNING；status 传 ALL/空/非法值按全部）。
     *
     * @param status  实例状态筛选（RUNNING/APPROVED/REJECTED，非法值按全部）
     * @param keyword 标题/业务单号关键字（模糊匹配，超长截断）
     */
    public PageResult<RunningInstanceView> runningInstances(int page, int size, String status,
                                                            String keyword) {
        String st = WorkflowMonitorRules.normalizeStatus(status);
        String kw = WorkflowMonitorRules.normalizeKeyword(keyword);
        Condition cond = buildInstanceCondition(st, kw);
        var query = PageQuery.of(page, size, null, null);
        PageResult<WfInstanceExtDO> raw = JooqWriters.page(dsl, WF_INSTANCE_EXT,
                WfInstanceExtDO.class, cond, query,
                JooqSorts.resolve(WF_INSTANCE_EXT, query, INSTANCE_SORT_FIELDS,
                        WF_INSTANCE_EXT.field("started_at", LocalDateTime.class).desc(),
                        WF_INSTANCE_EXT.field("id", Long.class).desc()));
        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> usernames = usernames(raw.records().stream()
                .map(WfInstanceExtDO::getStarterUserId).toList());
        List<RunningInstanceView> records = raw.records().stream()
                .map(e -> new RunningInstanceView(e.getProcessInstanceId(), e.getBusinessType(),
                        e.getBusinessId(), e.getTitle(), e.getStatus(), e.getStartedAt(),
                        WorkflowMonitorRules.stayedMinutes(e.getStartedAt(), now),
                        e.getCurrentActivityId(), e.getStarterUserId(),
                        usernames.get(e.getStarterUserId())))
                .toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), records);
    }

    // ------------------------------------------------------------------
    // 读面：超时任务
    // ------------------------------------------------------------------

    /**
     * 分页查询已到期未办结任务（按到期时间升序，最紧急在前）。
     *
     * <p>到期判定以引擎为准（{@code taskDueBefore}），与扩展表 {@code due_at} 回写同源；
     * 实例标题/办理人姓名经批量 IN 装载，避免逐行 N+1。</p>
     */
    public PageResult<OverdueTaskView> overdueTasks(int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        long total = engine.countOverdueTasks(now);
        if (total == 0L) {
            return PageResult.of(PageQuery.of(page, size).page(), PageQuery.of(page, size).size(),
                    0L, List.of());
        }
        var query = PageQuery.of(page, size);
        List<WorkflowEnginePort.TaskInfo> tasks =
                engine.overdueTasks(now, (int) query.offset(), query.limit());
        Map<String, WfInstanceExtDO> instances = instances(tasks.stream()
                .map(WorkflowEnginePort.TaskInfo::processInstanceId).toList());
        Map<Long, String> usernames = usernames(tasks.stream()
                .map(WorkflowEnginePort.TaskInfo::assigneeUserId).toList());
        List<OverdueTaskView> records = tasks.stream()
                .map(t -> {
                    WfInstanceExtDO ext = instances.get(t.processInstanceId());
                    return new OverdueTaskView(t.taskId(), t.name(), t.processInstanceId(),
                            ext == null ? null : ext.getTitle(),
                            ext == null ? null : ext.getBusinessId(),
                            t.activityId(), t.assigneeUserId(), usernames.get(t.assigneeUserId()),
                            t.dueAt(), WorkflowMonitorRules.overdueMinutes(t.dueAt(), now));
                })
                .toList();
        return PageResult.of(query.page(), query.size(), total, records);
    }

    // ------------------------------------------------------------------
    // 读面：定义版本链与统计
    // ------------------------------------------------------------------

    /** 查询某 processKey 的流程定义版本链（版本号降序）。 */
    public List<DefinitionVersionView> definitionVersions(String processKey) {
        return engine.definitionVersions(processKey).stream()
                .map(v -> new DefinitionVersionView(v.id(), v.version(), v.name(), v.key(),
                        v.suspended(), v.deploymentId(), v.deployTime()))
                .toList();
    }

    /** 监控统计摘要：运行实例 / 超时任务 / 待办任务 / 今日发起 / 今日办结。 */
    public MonitorStatsView stats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        return new MonitorStatsView(
                countInstances(WF_INSTANCE_EXT.field("status", String.class).eq("RUNNING")),
                engine.countOverdueTasks(now),
                JooqWriters.count(dsl, WF_TASK_EXT, DSL.and(
                        JooqWriters.notDeleted(WF_TASK_EXT),
                        WF_TASK_EXT.field("status", String.class).eq("PENDING"))),
                countInstances(WF_INSTANCE_EXT.field("started_at", LocalDateTime.class)
                        .greaterOrEqual(todayStart)),
                countInstances(WF_INSTANCE_EXT.field("finished_at", LocalDateTime.class)
                        .greaterOrEqual(todayStart)));
    }

    // ------------------------------------------------------------------
    // 写面：定义生命周期（挂起/激活）
    // ------------------------------------------------------------------

    /**
     * 挂起流程定义：阻止新实例发起（不影响在途实例）。
     *
     * <p>守卫：定义不存在 → 404；已挂起 → 409（幂等拒绝）。引擎层 {@code suspendProcessDefinitionById}
     * 默认仅挂起定义本身、不级联在途实例，符合「停用问题版本、放行在途业务」语义。</p>
     */
    public void suspendDefinition(String processDefinitionId) {
        WorkflowEnginePort.ProcessDefinitionState state = engine.processDefinitionState(processDefinitionId);
        if (state == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "流程定义不存在: " + processDefinitionId);
        }
        WorkflowDefinitionRules.assertTransition(
                WorkflowDefinitionRules.evaluateSuspend(state.suspended()));
        engine.suspendDefinition(processDefinitionId);
    }

    /**
     * 激活流程定义：恢复新实例发起能力。
     *
     * <p>守卫：定义不存在 → 404；已激活 → 409（幂等拒绝）。</p>
     */
    public void activateDefinition(String processDefinitionId) {
        WorkflowEnginePort.ProcessDefinitionState state = engine.processDefinitionState(processDefinitionId);
        if (state == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "流程定义不存在: " + processDefinitionId);
        }
        WorkflowDefinitionRules.assertTransition(
                WorkflowDefinitionRules.evaluateActivate(state.suspended()));
        engine.activateDefinition(processDefinitionId);
    }

    // ------------------------------------------------------------------
    // 写面：催办
    // ------------------------------------------------------------------

    /**
     * 催办任务：向当前办理人推送实时提醒。
     *
     * <p>守卫：任务不存在/已办结 → 404；任务无办理人（未认领的池化任务）→ 409
     * （先认领再催办）；距上次催办不足最小间隔 → 429（防消息轰炸，间隔文案透出便于前端提示）。
     * 推送与记录在同一事务：回滚时不会留下「已催办」却无通知的中间态。</p>
     */
    @Transactional
    public void remind(String taskId) {
        CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
        WorkflowEnginePort.TaskInfo task = engine.getTask(taskId);
        if (task == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "任务不存在或已完成");
        }
        if (task.assigneeUserId() == null) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "任务尚未认领，无办理人可催办");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastRemindAt = lastRemindAt(taskId);
        if (!WorkflowMonitorRules.canRemind(lastRemindAt, now,
                properties.getRemindMinIntervalMinutes())) {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED,
                    "催办太频繁，请间隔 " + properties.getRemindMinIntervalMinutes() + " 分钟后再试");
        }
        Map<String, Object> payload = Map.of("taskId", task.taskId(), "taskName", task.name(),
                "processInstanceId", task.processInstanceId(),
                "instanceId", task.processInstanceId(),
                "reminderUserId", principal.userId());
        int delivered = realtimePushService.sendToUsers(List.of(task.assigneeUserId()),
                RealtimePushService.TYPE_TODO_REMINDER, payload);
        saveRemindRecord(task, principal.userId());
        log.info("催办任务 taskId={} assignee={} 推送触达={} 操作人={}",
                task.taskId(), task.assigneeUserId(), delivered, principal.userId());
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private long countInstances(Condition extra) {
        return JooqWriters.count(dsl, WF_INSTANCE_EXT,
                DSL.and(JooqWriters.notDeleted(WF_INSTANCE_EXT), extra));
    }

    /** 实例筛选：逻辑删除恒成立；状态白名单命中才收敛；关键字对标题/业务单号 OR 模糊。 */
    private Condition buildInstanceCondition(String status, String keyword) {
        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(WF_INSTANCE_EXT));
        if (status != null) {
            conds.add(WF_INSTANCE_EXT.field("status", String.class).eq(status));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword + "%";
            conds.add(DSL.or(
                    WF_INSTANCE_EXT.field("title", String.class).like(like),
                    WF_INSTANCE_EXT.field("business_id", String.class).like(like)));
        }
        return DSL.and(conds);
    }

    /** 批量查用户名（单次 IN，查不到者不进 Map，由调用方回落展示 id）。 */
    private Map<Long, String> usernames(Collection<Long> ids) {
        List<Long> distinct = WorkflowMonitorRules.distinctIds(new ArrayList<>(ids));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<SysUserDO> users = JooqWriters.fetchList(dsl, USER, SysUserDO.class,
                USER.field("id", Long.class).in(distinct));
        Map<Long, String> map = new HashMap<>();
        users.forEach(u -> map.put(u.getId(), u.getUsername()));
        return map;
    }

    /** 批量查实例扩展信息（单次 IN，供超时任务列表回填标题/业务单号）。 */
    private Map<String, WfInstanceExtDO> instances(Collection<String> processInstanceIds) {
        List<String> distinct = processInstanceIds.stream()
                .filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        List<WfInstanceExtDO> rows = JooqWriters.fetchList(dsl, WF_INSTANCE_EXT,
                WfInstanceExtDO.class,
                WF_INSTANCE_EXT.field("process_instance_id", String.class).in(distinct));
        Map<String, WfInstanceExtDO> map = new HashMap<>();
        rows.forEach(e -> map.put(e.getProcessInstanceId(), e));
        return map;
    }

    /** 该任务最近一次催办时刻（无记录返回 null，表示从未催办）。 */
    private LocalDateTime lastRemindAt(String taskId) {
        List<WfApprovalRecordDO> rows = JooqWriters.fetchList(dsl, WF_APPROVAL_RECORD,
                WfApprovalRecordDO.class,
                DSL.and(WF_APPROVAL_RECORD.field("task_id", String.class).eq(taskId),
                        WF_APPROVAL_RECORD.field("action", String.class).eq(ACTION_REMIND)),
                WF_APPROVAL_RECORD.field("occurred_at", LocalDateTime.class).desc());
        return rows.isEmpty() ? null : rows.get(0).getOccurredAt();
    }

    /**
     * 催办记录：复用审批记录表（action=REMIND，from/to 为空，opinion 记录最小间隔分钟数便于审计），
     * traceId 透传便于与链路关联。operation_id 用 {@code remind-<taskId>-<时间戳>} 保证唯一。
     */
    private void saveRemindRecord(WorkflowEnginePort.TaskInfo task, Long operatorId) {
        WfApprovalRecordDO record = new WfApprovalRecordDO();
        record.setOperationId("remind-" + task.taskId() + "-" + System.currentTimeMillis());
        record.setProcessInstanceId(task.processInstanceId());
        record.setTaskId(task.taskId());
        record.setActivityId(task.activityId());
        record.setAction(ACTION_REMIND);
        record.setOperatorUserId(operatorId);
        record.setOpinion("minInterval=" + properties.getRemindMinIntervalMinutes());
        record.setTraceId(org.slf4j.MDC.get("traceId"));
        record.setOccurredAt(LocalDateTime.now());
        JooqWriters.insert(dsl, WF_APPROVAL_RECORD, record);
    }
}
