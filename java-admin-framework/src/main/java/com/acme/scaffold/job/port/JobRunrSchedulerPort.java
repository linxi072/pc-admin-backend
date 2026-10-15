package com.acme.scaffold.job.port;

import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.acme.scaffold.job.service.JobExecutor;

/**
 * {@link JobSchedulerPort} 的 JobRunr 实现。
 *
 * <p>要点：
 * <ul>
 *   <li><b>延迟获取调度器</b>：与 {@code JobRunrConfig} 一致，采用 {@link ObjectProvider} 而非构造器直注，
 *       避免普通 {@code @Configuration}/{@code @Component} 早于自动配置解析导致 Bean 尚不可用；
 *       运行期按需判断 {@link #available()}；</li>
 *   <li><b>任务体</b>：统一调度 {@link JobExecutor#run(Long)}（Spring Bean 方法），
 *       JobRunr 通过 {@code JobActivator} 反查 Bean 实例执行，无需实现任何框架接口；</li>
 *   <li><b>幂等取消</b>：暂停时对已执行/已删除的 Job 取消会异常，此处统一吞并降级为告警日志。</li>
 * </ul>
 */
@Slf4j
@Component
public class JobRunrSchedulerPort implements JobSchedulerPort {

    /** 周期任务 id 前缀，用于在 JobRunr 存储中区分自管任务。 */
    private static final String RECURRING_PREFIX = "sysjob-";

    private final ObjectProvider<JobScheduler> jobSchedulerProvider;
    private final JobExecutor jobExecutor;

    public JobRunrSchedulerPort(ObjectProvider<JobScheduler> jobSchedulerProvider, JobExecutor jobExecutor) {
        this.jobSchedulerProvider = jobSchedulerProvider;
        this.jobExecutor = jobExecutor;
    }

    @Override
    public boolean available() {
        return jobSchedulerProvider.getIfAvailable() != null;
    }

    @Override
    public String scheduleCron(Long jobId, String cron) {
        JobScheduler scheduler = requireScheduler();
        String ref = RECURRING_PREFIX + jobId;
        scheduler.scheduleRecurrently(ref, cron, () -> jobExecutor.run(jobId));
        log.info("已注册 CRON 周期任务 ref={} cron={} jobId={}", ref, cron, jobId);
        return ref;
    }

    @Override
    public String scheduleInterval(Long jobId, Duration interval) {
        JobScheduler scheduler = requireScheduler();
        String ref = RECURRING_PREFIX + jobId;
        scheduler.scheduleRecurrently(ref, interval, () -> jobExecutor.run(jobId));
        log.info("已注册间隔周期任务 ref={} interval={} jobId={}", ref, interval, jobId);
        return ref;
    }

    @Override
    public String scheduleOneTime(Long jobId, Instant fireAt) {
        JobScheduler scheduler = requireScheduler();
        UUID uuid = UUID.randomUUID();
        scheduler.schedule(uuid, fireAt, () -> jobExecutor.run(jobId));
        log.info("已注册一次性任务 uuid={} fireAt={} jobId={}", uuid, fireAt, jobId);
        return uuid.toString();
    }

    @Override
    public void cancelRecurring(String recurringRef) {
        if (recurringRef == null || recurringRef.isBlank()) {
            return;
        }
        JobScheduler scheduler = jobSchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.delete(recurringRef);
            log.info("已取消周期任务 ref={}", recurringRef);
        } catch (RuntimeException e) {
            // 已被删除/从未注册：视为幂等成功
            log.warn("取消周期任务失败（按幂等忽略）ref={} err={}", recurringRef, e.toString());
        }
    }

    @Override
    public void cancelOneTime(String uuidRef) {
        if (uuidRef == null || uuidRef.isBlank()) {
            return;
        }
        JobScheduler scheduler = jobSchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return;
        }
        try {
            scheduler.delete(UUID.fromString(uuidRef));
            log.info("已取消一次性任务 uuid={}", uuidRef);
        } catch (RuntimeException e) {
            // 已执行完毕或不存在：视为幂等成功
            log.warn("取消一次性任务失败（按幂等忽略）uuid={} err={}", uuidRef, e.toString());
        }
    }

    private JobScheduler requireScheduler() {
        JobScheduler scheduler = jobSchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            throw new IllegalStateException("JobScheduler 不可用，无法注册定时任务"
                    + "（请检查 jobrunr.job-scheduler.enabled）");
        }
        return scheduler;
    }
}
