package com.acme.scaffold.job.management;

import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.job.management.dto.JobCreateDTO;
import com.acme.scaffold.job.management.dto.JobHandlerOptionVO;
import com.acme.scaffold.job.management.dto.JobLogVO;
import com.acme.scaffold.job.management.dto.JobQuery;
import com.acme.scaffold.job.management.dto.JobUpdateDTO;
import com.acme.scaffold.job.management.dto.JobVO;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 定时任务管理服务：任务配置、启动、暂停、列表与实时状态、执行日志。
 *
 * <p><b>双层架构</b>：任务定义与执行日志自管于 {@code sys_job / sys_job_log}；
 * 调度交由 {@link JobSchedulerPort}（JobRunr 实现）。二者通过 {@code scheduler_ref} 关联。
 *
 * <p><b>即时生效</b>：
 * <ul>
 *   <li>新建默认 {@code PAUSED} 且不调度，避免误触发；</li>
 *   <li>启动 → 立即向调度器注册（CRON/SIMPLE/ONE_TIME），任务即刻按规则参与调度；</li>
 *   <li>暂停 → 立即取消调度；</li>
 *   <li>编辑 → 运行中任务先取消调度，写入新配置后按新配置重启，变换即时生效。</li>
 * </ul>
 *
 * <p><b>事务说明</b>：本类每个写操作均为单条 DB 语句（原子），故不声明 {@code @Transactional}——
 * 若包在事务内，调度器（JobRunr 使用独立事务）的注册/取消会先于业务事务提交生效，
 * 一旦回滚将残留「孤儿调度」。以「先取消旧调度 → 落库 → 再注册新调度」的顺序执行更安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String DEFAULT_GROUP = "DEFAULT";

    private final DSLContext dsl;
    private final JobSchedulerPort schedulerPort;
    private final JobHandlerRegistry handlerRegistry;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 分页查询任务列表，{@code nextFireTime} 为实时计算值。 */
    public PageResult<JobVO> page(JobQuery query) {
        Condition cond = buildCondition(query);
        PageQuery pq = PageQuery.of(query.page(), query.size());
        PageResult<SysJobDO> raw = JooqWriters.page(dsl, JooqTables.SYS_JOB, SysJobDO.class, cond, pq,
                JooqTables.SYS_JOB.field("id", Long.class).desc());
        List<JobVO> records = raw.records().stream().map(this::toVO).toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), records);
    }

    /** 任务详情。 */
    public JobVO detail(Long id) {
        return toVO(require(id));
    }

    /** 任务执行日志（按触发时间倒序）。 */
    public PageResult<JobLogVO> logs(Long jobId, int page, int size) {
        require(jobId);
        Condition cond = DSL.and(
                JooqTables.SYS_JOB_LOG.field("tenant_id", Long.class).eq(0L),
                JooqTables.SYS_JOB_LOG.field("job_id", Long.class).eq(jobId));
        PageResult<SysJobLogDO> raw = JooqWriters.page(dsl, JooqTables.SYS_JOB_LOG, SysJobLogDO.class, cond,
                PageQuery.of(page, size),
                JooqTables.SYS_JOB_LOG.field("fire_time", LocalDateTime.class).desc(),
                JooqTables.SYS_JOB_LOG.field("id", Long.class).desc());
        List<JobLogVO> records = raw.records().stream()
                .map(r -> new JobLogVO(r.getId(), r.getJobId(), r.getFireTime(), r.getFinishedTime(),
                        r.getDurationMs(), r.getStatus(), r.getErrorMessage(), r.getTraceId()))
                .toList();
        return PageResult.of(raw.page(), raw.size(), raw.total(), records);
    }

    /** 可选处理器列表（前端下拉）。 */
    public List<JobHandlerOptionVO> handlerOptions() {
        return handlerRegistry.all().values().stream()
                .map(h -> new JobHandlerOptionVO(h.key(), h.name()))
                .toList();
    }

    /** 调度引擎是否可用（前端据此提示）。 */
    public boolean schedulerAvailable() {
        return schedulerPort.available();
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /** 新建任务（默认暂停、不调度），返回主键。 */
    public Long create(JobCreateDTO dto) {
        JobTriggerType type = requireTriggerType(dto.triggerType());
        JobIntervalUnit unit = intervalUnitOrNull(dto.intervalUnit());
        Instant now = Instant.now();
        Instant oneTime = toInstant(dto.oneTimeAt());
        JobValidator.validate(dto.jobName(), dto.taskKey(), type, dto.cronExpression(),
                dto.intervalValue(), unit, oneTime, dto.jobParams(), now);
        requireHandler(dto.taskKey());

        SysJobDO job = new SysJobDO();
        applyEditableFields(job, dto.jobName(), dto.jobGroup(), dto.taskKey(), type,
                dto.cronExpression(), dto.intervalValue(), unit, dto.oneTimeAt(),
                dto.jobParams(), dto.remark());
        job.setStatus(JobStatus.PAUSED.name());
        job.setTotalRuns(0L);
        job.setSuccessRuns(0L);
        job.setFailureRuns(0L);
        JooqWriters.insert(dsl, JooqTables.SYS_JOB, job);
        log.info("新建定时任务 id={} name={} type={}（默认暂停，未调度）", job.getId(), job.getJobName(), type);
        return job.getId();
    }

    /** 编辑任务：运行中先取消调度，写入新配置后按新配置重启（即时生效）。 */
    public void update(Long id, JobUpdateDTO dto) {
        SysJobDO existing = require(id);
        JobTriggerType type = requireTriggerType(dto.triggerType());
        JobIntervalUnit unit = intervalUnitOrNull(dto.intervalUnit());
        Instant now = Instant.now();
        Instant oneTime = toInstant(dto.oneTimeAt());
        JobValidator.validate(dto.jobName(), dto.taskKey(), type, dto.cronExpression(),
                dto.intervalValue(), unit, oneTime, dto.jobParams(), now);
        requireHandler(dto.taskKey());

        boolean wasRunning = JobStatus.RUNNING.name().equals(existing.getStatus());
        if (wasRunning) {
            cancelSchedule(existing);
        }

        SysJobDO patch = new SysJobDO();
        applyEditableFields(patch, dto.jobName(), dto.jobGroup(), dto.taskKey(), type,
                dto.cronExpression(), dto.intervalValue(), unit, dto.oneTimeAt(),
                dto.jobParams(), dto.remark());
        patch.setStatus(JobStatus.PAUSED.name());

        // 需显式置空的列：未使用的触发字段 + 调度引用 + 下次执行时间快照 + 被清空的文本字段
        Set<String> nullColumns = new HashSet<>(unusedTriggerColumns(type));
        nullColumns.add("scheduler_ref");
        nullColumns.add("next_fire_time");
        if (blankToNull(dto.jobParams()) == null) {
            nullColumns.add("job_params");
        }
        if (blankToNull(dto.remark()) == null) {
            nullColumns.add("remark");
        }
        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_JOB, id, patch, nullColumns);
        log.info("更新定时任务 id={} name={} type={}（原运行中={}）", id, dto.jobName(), type, wasRunning);

        if (wasRunning) {
            // 按新配置重启，保证「即时生效」
            start(id);
        }
    }

    /** 启动任务：立即向调度器注册并按类型计算下次执行时间。幂等（重复启动会先取消旧调度再注册）。 */
    public void start(Long id) {
        SysJobDO job = require(id);
        JobTriggerType type = requireTriggerType(job.getTriggerType());
        requireHandler(job.getTaskKey());
        if (!schedulerPort.available()) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "调度引擎不可用，暂无法启动任务");
        }
        // 幂等：先取消可能存在的旧调度，避免一次性任务重复注册
        cancelSchedule(job);

        Instant now = Instant.now();
        String ref;
        Instant nextFire;
        switch (type) {
            case CRON -> {
                ref = schedulerPort.scheduleCron(id, job.getCronExpression());
                nextFire = JobNextFire.compute(type, job.getCronExpression(), null, null,
                        null, null, now, ZONE);
            }
            case SIMPLE -> {
                JobIntervalUnit unit = intervalUnitOrNull(job.getIntervalUnit());
                if (unit == null || job.getIntervalValue() == null || job.getIntervalValue() <= 0) {
                    throw BusinessException.badRequest("任务间隔配置不完整，无法启动");
                }
                ref = schedulerPort.scheduleInterval(id, unit.toDuration(job.getIntervalValue()));
                nextFire = JobNextFire.compute(type, null, job.getIntervalValue(), unit,
                        null, toInstant(job.getLastFireTime()), now, ZONE);
            }
            case ONE_TIME -> {
                Instant fireAt = toInstant(job.getOneTimeAt());
                if (fireAt == null || !fireAt.isAfter(now)) {
                    throw BusinessException.badRequest("单次执行时间已过，请修改执行时间后再启动");
                }
                ref = schedulerPort.scheduleOneTime(id, fireAt);
                nextFire = fireAt;
            }
            default -> throw BusinessException.badRequest("不支持的触发类型");
        }

        SysJobDO patch = new SysJobDO();
        patch.setStatus(JobStatus.RUNNING.name());
        patch.setSchedulerRef(ref);
        if (nextFire != null) {
            patch.setNextFireTime(LocalDateTime.ofInstant(nextFire, ZONE));
            JooqWriters.updateById(dsl, JooqTables.SYS_JOB, id, patch);
        } else {
            JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_JOB, id, patch,
                    Set.of("next_fire_time"));
        }
        log.info("启动定时任务 id={} name={} type={} ref={} nextFire={}",
                id, job.getJobName(), type, ref, nextFire);
    }

    /** 暂停任务：立即取消调度并清空调度引用与下次执行时间。 */
    public void pause(Long id) {
        SysJobDO job = require(id);
        cancelSchedule(job);
        SysJobDO patch = new SysJobDO();
        patch.setStatus(JobStatus.PAUSED.name());
        JooqWriters.updateByIdWithNulls(dsl, JooqTables.SYS_JOB, id, patch,
                Set.of("scheduler_ref", "next_fire_time"));
        log.info("暂停定时任务 id={} name={}", id, job.getJobName());
    }

    /** 删除任务（逻辑删除）：运行中先取消调度。执行日志保留以便追溯。 */
    public void remove(Long id) {
        SysJobDO job = require(id);
        cancelSchedule(job);
        JooqWriters.delete(dsl, JooqTables.SYS_JOB, id, true);
        log.info("删除定时任务 id={} name={}", id, job.getJobName());
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private SysJobDO require(Long id) {
        SysJobDO job = JooqWriters.fetchById(dsl, JooqTables.SYS_JOB, SysJobDO.class, id);
        if (job == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "定时任务不存在");
        }
        return job;
    }

    private void requireHandler(String taskKey) {
        if (!handlerRegistry.contains(taskKey)) {
            throw BusinessException.badRequest("未注册的任务处理器：" + taskKey);
        }
    }

    /** 按触发类型取消调度（幂等）。 */
    private void cancelSchedule(SysJobDO job) {
        String ref = job.getSchedulerRef();
        if (ref == null || ref.isBlank()) {
            return;
        }
        JobTriggerType type = triggerTypeOrNull(job.getTriggerType());
        if (type == JobTriggerType.ONE_TIME) {
            schedulerPort.cancelOneTime(ref);
        } else {
            schedulerPort.cancelRecurring(ref);
        }
    }

    private void applyEditableFields(SysJobDO target, String jobName, String jobGroup, String taskKey,
                                     JobTriggerType type, String cron, Integer intervalValue,
                                     JobIntervalUnit unit, LocalDateTime oneTimeAt,
                                     String params, String remark) {
        target.setJobName(jobName.trim());
        target.setJobGroup(normalizeGroup(jobGroup));
        target.setTaskKey(taskKey.trim());
        target.setTriggerType(type.name());
        switch (type) {
            case CRON -> target.setCronExpression(cron.trim());
            case SIMPLE -> {
                target.setIntervalValue(intervalValue);
                target.setIntervalUnit(unit.name());
            }
            case ONE_TIME -> target.setOneTimeAt(oneTimeAt);
        }
        target.setJobParams(blankToNull(params));
        target.setRemark(blankToNull(remark));
    }

    /** 当前触发类型未使用的列（编辑时显式置空，保持数据整洁）。 */
    private static Set<String> unusedTriggerColumns(JobTriggerType type) {
        return switch (type) {
            case CRON -> Set.of("interval_value", "interval_unit", "one_time_at");
            case SIMPLE -> Set.of("cron_expression", "one_time_at");
            case ONE_TIME -> Set.of("cron_expression", "interval_value", "interval_unit");
        };
    }

    private JobVO toVO(SysJobDO job) {
        JobTriggerType type = triggerTypeOrNull(job.getTriggerType());
        JobStatus status = JobStatus.parse(job.getStatus());
        JobIntervalUnit unit = intervalUnitOrNull(job.getIntervalUnit());
        String handlerName = handlerRegistry.find(job.getTaskKey())
                .map(JobHandler::name)
                .orElse(job.getTaskKey());
        return new JobVO(
                job.getId(), job.getJobName(), job.getJobGroup(), job.getTaskKey(), handlerName,
                job.getTriggerType(), type == null ? null : type.getLabel(),
                job.getCronExpression(), job.getIntervalValue(), job.getIntervalUnit(),
                intervalLabel(job.getIntervalValue(), unit),
                job.getOneTimeAt(), job.getJobParams(),
                job.getStatus(), status == null ? job.getStatus() : status.getLabel(),
                job.getLastFireTime(), liveNextFire(job),
                nz(job.getTotalRuns()), nz(job.getSuccessRuns()), nz(job.getFailureRuns()),
                job.getLastStatus(), job.getLastError(), job.getLastDurationMs(),
                job.getRemark(), job.getCreatedAt(), job.getUpdatedAt());
    }

    /** 实时计算下次执行时间（仅运行中任务展示；暂停/完成返回 null）。 */
    private LocalDateTime liveNextFire(SysJobDO job) {
        if (!JobStatus.RUNNING.name().equals(job.getStatus())) {
            return null;
        }
        JobTriggerType type = triggerTypeOrNull(job.getTriggerType());
        if (type == null) {
            return null;
        }
        Instant next = JobNextFire.compute(type, job.getCronExpression(), job.getIntervalValue(),
                intervalUnitOrNull(job.getIntervalUnit()), toInstant(job.getOneTimeAt()),
                toInstant(job.getLastFireTime()), Instant.now(), ZONE);
        return next == null ? null : LocalDateTime.ofInstant(next, ZONE);
    }

    private Condition buildCondition(JobQuery query) {
        var t = JooqTables.SYS_JOB;
        Condition cond = DSL.and(
                t.field("tenant_id", Long.class).eq(0L),
                JooqWriters.notDeleted(t));
        if (notBlank(query.jobName())) {
            cond = DSL.and(cond, t.field("job_name", String.class)
                    .likeIgnoreCase("%" + query.jobName().trim() + "%"));
        }
        if (notBlank(query.jobGroup())) {
            cond = DSL.and(cond, t.field("job_group", String.class).eq(query.jobGroup().trim()));
        }
        if (notBlank(query.status())) {
            cond = DSL.and(cond, t.field("status", String.class).eq(query.status().trim().toUpperCase()));
        }
        if (notBlank(query.triggerType())) {
            cond = DSL.and(cond, t.field("trigger_type", String.class)
                    .eq(query.triggerType().trim().toUpperCase()));
        }
        if (notBlank(query.taskKey())) {
            cond = DSL.and(cond, t.field("task_key", String.class).eq(query.taskKey().trim()));
        }
        return cond;
    }

    // ------------------------------------------------------------------
    // 静态工具（供 JobExecutor 复用）
    // ------------------------------------------------------------------

    /** 宽松解析触发类型：非法/空返回 null（用于读取存量数据，不抛异常）。 */
    public static JobTriggerType triggerTypeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JobTriggerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 宽松解析间隔单位：非法/空返回 null。 */
    public static JobIntervalUnit intervalUnitOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JobIntervalUnit.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 严格解析触发类型：非法/空抛 400（用于请求参数）。 */
    private static JobTriggerType requireTriggerType(String value) {
        JobTriggerType type = triggerTypeOrNull(value);
        if (type == null) {
            throw BusinessException.badRequest("触发类型非法：" + value);
        }
        return type;
    }

    private static String intervalLabel(Integer value, JobIntervalUnit unit) {
        if (value == null || unit == null) {
            return null;
        }
        return value + " " + unit.getLabel();
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toInstant();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static String normalizeGroup(String group) {
        return blankToNull(group) == null ? DEFAULT_GROUP : group.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
