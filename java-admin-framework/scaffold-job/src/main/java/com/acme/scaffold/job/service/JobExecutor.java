package com.acme.scaffold.job.service;

import com.acme.scaffold.job.domain.JobExecutionContext;
import com.acme.scaffold.job.domain.JobHandler;
import com.acme.scaffold.job.domain.JobNextFire;
import com.acme.scaffold.job.domain.JobStatus;
import com.acme.scaffold.job.domain.JobTriggerType;
import com.acme.scaffold.job.service.JobDagService;
import com.acme.scaffold.job.entity.SysJobDO;
import com.acme.scaffold.job.entity.SysJobLogDO;
import com.acme.scaffold.job.port.JobInvoker;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 任务执行器：调度器到点触发后由 JobRunr 回调本类的 {@link #run(Long)}。
 *
 * <p>执行流程：加载任务定义 → 解析处理器 → 执行 → 回写统计（{@code last_fire_time / next_fire_time /
 * total_runs / success_runs / failure_runs / last_status / last_error / last_duration_ms}）→ 写入执行日志。
 *
 * <p>事务边界：<b>不</b>将整个执行包在事务内。处理器可能包含较慢的外部调用，长事务会占用连接并放大锁范围；
 * 统计与日志的写入各自为独立短事务即可，失败不掩盖业务执行结果。
 *
 * <p>一次性任务（ONE_TIME）执行完成后置为 {@link JobStatus#COMPLETED} 并清空下次执行时间。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor implements JobInvoker {
    private static final JooqTables.TableRef SYS_JOB = JooqTables.SYS_JOB;
    private static final JooqTables.TableRef SYS_JOB_LOG = JooqTables.SYS_JOB_LOG;


    private static final int MAX_ERROR_LEN = 900;
    private static final int MAX_LOG_ERROR_LEN = 900;

    private final DSLContext dsl;
    private final JobHandlerRegistry handlerRegistry;
    private final JobDagService jobDagService;

    /** 供调度器回调的入口（JobRunr 通过 JobActivator 反查本 Bean）。 */
    public void run(Long jobId) {
        if (jobId == null) {
            log.warn("定时任务触发但 jobId 为空，忽略");
            return;
        }
        SysJobDO job = JooqWriters.fetchById(dsl, SYS_JOB, SysJobDO.class, jobId);
        if (job == null) {
            log.warn("定时任务触发但任务不存在或已删除：jobId={}", jobId);
            return;
        }
        // 复核调度状态：「暂停」前已入队的下一次触发仍会到达，若不校验就会出现「暂停后仍跑一次」。
        // 允许 RUNNING（常规调度）与 WAITING（依赖编排由上游成功事件即时触发的下游）两种执行态。
        String status = job.getStatus();
        if (!JobStatus.RUNNING.name().equals(status) && !JobStatus.WAITING.name().equals(status)) {
            log.info("定时任务已非运行中/等待依赖，跳过本次触发：jobId={}, status={}", jobId, status);
            return;
        }

        LocalDateTime fireTime = LocalDateTime.now();
        Instant fireInstant = Instant.now();
        long start = System.currentTimeMillis();

        boolean success = true;
        String error = null;
        Optional<JobHandler> handler = handlerRegistry.find(job.getTaskKey());
        if (handler.isEmpty()) {
            success = false;
            error = "未找到任务处理器: " + job.getTaskKey();
            log.warn("定时任务 {} 执行失败：{}", jobId, error);
        } else {
            JobExecutionContext ctx = new JobExecutionContext(
                    job.getId(), job.getJobName(), job.getTaskKey(), job.getJobParams());
            try {
                handler.get().execute(ctx);
            } catch (Exception e) {
                success = false;
                error = truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                        MAX_ERROR_LEN);
                log.error("定时任务执行失败 jobId={} jobName={}", jobId, job.getJobName(), e);
            }
        }

        long cost = System.currentTimeMillis() - start;
        LocalDateTime finishedTime = LocalDateTime.now();
        recordResult(job, fireTime, finishedTime, cost, success, error, fireInstant);

        // 依赖编排：成功执行后驱动就绪的下游任务（失败不影响本次结果）。onJobSuccess 内部已做幂等与异常隔离，
        // 此处再包一层 try/catch 确保 DAG 编排异常绝不回滚或掩盖本次任务执行结果。
        if (success) {
            try {
                jobDagService.onJobSuccess(jobId);
            } catch (Exception e) {
                log.error("任务成功后驱动下游依赖失败（不影响本次执行结果）jobId={}", jobId, e);
            }
        }
    }

    private void recordResult(SysJobDO job, LocalDateTime fireTime, LocalDateTime finishedTime,
                              long cost, boolean success, String error, Instant fireInstant) {
        JobTriggerType type = JobService.triggerTypeOrNull(job.getTriggerType());
        ZoneId zone = ZoneId.systemDefault();
        boolean completed = type == JobTriggerType.ONE_TIME;

        SysJobDO patch = new SysJobDO();
        patch.setLastFireTime(fireTime);
        patch.setLastStatus(success ? "SUCCESS" : "FAILED");
        patch.setLastDurationMs(cost);
        patch.setTotalRuns(nz(job.getTotalRuns()) + 1);
        patch.setSuccessRuns(nz(job.getSuccessRuns()) + (success ? 1 : 0));
        patch.setFailureRuns(nz(job.getFailureRuns()) + (success ? 0 : 1));
        if (!success && error != null) {
            patch.setLastError(error);
        }

        Set<String> nullColumns = new HashSet<>();
        // 执行成功时清空历史错误信息，避免陈旧错误误导排查
        if (success) {
            nullColumns.add("last_error");
        }

        Instant next = completed ? null : JobNextFire.compute(type, job.getCronExpression(),
                job.getIntervalValue(), JobService.intervalUnitOrNull(job.getIntervalUnit()),
                toInstant(job.getOneTimeAt(), zone), fireInstant, fireInstant, zone);
        if (next != null) {
            patch.setNextFireTime(LocalDateTime.ofInstant(next, zone));
        } else {
            nullColumns.add("next_fire_time");
        }
        if (completed) {
            patch.setStatus(JobStatus.COMPLETED.name());
        }

        if (nullColumns.isEmpty()) {
            JooqWriters.updateById(dsl, SYS_JOB, job.getId(), patch);
        } else {
            JooqWriters.updateByIdWithNulls(dsl, SYS_JOB, job.getId(), patch, nullColumns);
        }

        SysJobLogDO logRow = new SysJobLogDO();
        logRow.setJobId(job.getId());
        logRow.setJobName(job.getJobName());
        logRow.setTaskKey(job.getTaskKey());
        logRow.setTriggerType(job.getTriggerType());
        logRow.setFireTime(fireTime);
        logRow.setFinishedTime(finishedTime);
        logRow.setDurationMs(cost);
        logRow.setStatus(success ? "SUCCESS" : "FAILED");
        logRow.setErrorMessage(error == null ? null : truncate(error, MAX_LOG_ERROR_LEN));
        logRow.setTraceId(MDC.get("traceId"));
        JooqWriters.insert(dsl, SYS_JOB_LOG, logRow);
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static Instant toInstant(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toInstant();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
