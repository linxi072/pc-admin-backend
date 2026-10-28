package com.acme.scaffold.job.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.job.domain.JobDagRules;
import com.acme.scaffold.job.domain.JobIntervalUnit;
import com.acme.scaffold.job.domain.JobNextFire;
import com.acme.scaffold.job.domain.JobStatus;
import com.acme.scaffold.job.domain.JobTriggerType;
import com.acme.scaffold.job.entity.SysJobDO;
import com.acme.scaffold.job.port.JobDagPort;
import com.acme.scaffold.job.port.JobSchedulerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 任务依赖编排（DAG）编排服务：基于 {@code sys_job.depends_on} 表达的有向无环依赖，
 * 在「上游任务成功执行」事件驱动下，自动触发就绪（依赖全部满足）的下游任务。
 *
 * <p><b>语义</b>：任务 A 依赖 B 表示「B 至少成功执行一次后，才触发 A」。依赖不满足时 A 处于
 * {@link JobStatus#WAITING} 且不被自身调度；上游成功事件到达后由 {@link #onJobSuccess} 即时触发就绪的下游。
 *
 * <p><b>设计边界</b>：
 * <ul>
 *   <li>不持有调度细节——依赖图由 {@link JobDagPort} 提供；</li>
 *   <li>即时触发复用 {@link JobSchedulerPort}（按上游任务自身的触发类型注册：CRON/SIMPLE 注册周期，
 *       ONE_TIME 注册一次性），触发后由 {@link JobExecutor} 照常执行并回写状态；</li>
 *   <li>仅对 {@code WAITING} 态的下游触发，已运行/暂停的下游不会被重复调度，保证幂等；</li>
 *   <li>依赖「至少成功一次」以 {@code success_runs > 0} 或终态 {@code COMPLETED} 判定，
 *       因此依赖周期任务时「其首次成功」即可驱动下游，无需等待其结束。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDagService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JobDagPort dagPort;
    private final JobSchedulerPort schedulerPort;

    /**
     * 某任务成功执行后，驱动其就绪的依赖方（下游）即时触发。
     * 幂等：仅对 {@code WAITING} 且依赖全部满足者触发；无下游或下游未就绪则空操作。
     */
    public void onJobSuccess(Long jobId) {
        List<SysJobDO> dependents = dagPort.findDependents(jobId);
        for (SysJobDO dep : dependents) {
            if (JobStatus.WAITING.name().equals(dep.getStatus()) && isReady(dep)) {
                triggerReady(dep);
            }
        }
    }

    /** 手动触发某任务的所有就绪下游（Admin 主动操作）。返回实际触发的下游数量。 */
    public int triggerDownstream(Long id) {
        List<SysJobDO> dependents = dagPort.findDependents(id);
        int count = 0;
        for (SysJobDO dep : dependents) {
            if (JobStatus.WAITING.name().equals(dep.getStatus()) && isReady(dep)) {
                if (triggerReady(dep)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 某任务的下游依赖 ID 列表（用于详情/列表展示）。 */
    public List<Long> dependentsOf(Long id) {
        return dagPort.dependentIds(id);
    }

    /** 批量返回「任务ID → 下游依赖ID列表」。 */
    public Map<Long, List<Long>> dependentsMap(Set<Long> ids) {
        return dagPort.dependentsMap(ids);
    }

    /** 删除守卫：若存在其他任务依赖本任务，拒绝删除（避免悬空依赖）。 */
    public void assertNoDependents(Long id) {
        List<Long> dependents = dagPort.dependentIds(id);
        if (!dependents.isEmpty()) {
            throw BusinessException.badRequest("该任务被其他任务依赖，无法删除，请先解除依赖: " + dependents);
        }
    }

    /** 将任务置为 {@link JobStatus#WAITING}（精确写，不触碰其他列）。 */
    public void setWaiting(Long id) {
        dagPort.setStatus(id, JobStatus.WAITING.name());
    }

    /**
     * 判断任务是否可启动：无依赖 → 永远可启动；有依赖 → 要求每个依赖均已至少成功执行一次
     * （{@link JobStatus#COMPLETED} 或 {@code success_runs > 0}）。
     */
    public boolean isReady(Long id) {
        return dagPort.findById(id).map(this::isReady).orElse(false);
    }

    private boolean isReady(SysJobDO job) {
        List<Long> deps = JobDagRules.parseDependsOn(job.getDependsOn());
        if (deps.isEmpty()) {
            return true;
        }
        for (Long dep : deps) {
            Optional<SysJobDO> d = dagPort.findById(dep);
            if (d.isEmpty()) {
                continue; // 上游已删除 → 视为已满足（避免悬空边永久阻塞）
            }
            SysJobDO depJob = d.get();
            boolean satisfied = JobStatus.COMPLETED.name().equals(depJob.getStatus())
                    || (depJob.getSuccessRuns() != null && depJob.getSuccessRuns() > 0);
            if (!satisfied) {
                return false;
            }
        }
        return true;
    }

    /** 编辑依赖时的校验：存在性 + 环检测（自依赖由 {@link JobDagRules} 内部拦截）。 */
    public void validateDependencyEdit(Long selfId, List<Long> newDeps) {
        Set<Long> allIds = dagPort.allIds();
        JobDagRules.validateDependencies(selfId, newDeps, allIds);
        if (newDeps == null || newDeps.isEmpty()) {
            return;
        }
        Function<Long, List<Long>> depsOf = candidate -> {
            if (candidate.equals(selfId)) {
                return JobDagRules.dedupe(newDeps);
            }
            return dagPort.findById(candidate)
                    .map(j -> JobDagRules.parseDependsOn(j.getDependsOn()))
                    .orElse(List.of());
        };
        if (JobDagRules.hasCycle(selfId, depsOf)) {
            throw BusinessException.badRequest("依赖配置存在环，无法保存");
        }
    }

    /** 将 WAITING 任务翻为 RUNNING 并按其触发类型注册调度（即时触发）。 */
    private boolean triggerReady(SysJobDO job) {
        if (!JobStatus.WAITING.name().equals(job.getStatus()) || !schedulerPort.available()) {
            return false;
        }
        JobTriggerType type = JobService.triggerTypeOrNull(job.getTriggerType());
        if (type == null) {
            return false;
        }
        Instant now = Instant.now();
        String ref;
        Instant nextFire;
        switch (type) {
            case CRON -> {
                ref = schedulerPort.scheduleCron(job.getId(), job.getCronExpression());
                nextFire = JobNextFire.compute(type, job.getCronExpression(), null, null,
                        null, null, now, ZONE);
            }
            case SIMPLE -> {
                JobIntervalUnit unit = JobService.intervalUnitOrNull(job.getIntervalUnit());
                if (unit == null || job.getIntervalValue() == null || job.getIntervalValue() <= 0) {
                    return false;
                }
                ref = schedulerPort.scheduleInterval(job.getId(), unit.toDuration(job.getIntervalValue()));
                nextFire = JobNextFire.compute(type, null, job.getIntervalValue(), unit,
                        null, toInstant(job.getLastFireTime()), now, ZONE);
            }
            case ONE_TIME -> {
                nextFire = now.plusSeconds(2);
                ref = schedulerPort.scheduleOneTime(job.getId(), nextFire);
            }
            default -> {
                return false;
            }
        }
        dagPort.setRunning(job.getId(), ref, LocalDateTime.ofInstant(nextFire, ZONE));
        log.info("依赖满足，即时触发下游任务 id={} name={} type={} nextFire={}",
                job.getId(), job.getJobName(), type, nextFire);
        return true;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toInstant();
    }
}
