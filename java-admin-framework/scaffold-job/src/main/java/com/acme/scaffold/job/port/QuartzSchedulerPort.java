package com.acme.scaffold.job.port;

import com.acme.scaffold.job.domain.QuartzCron;
import com.acme.scaffold.job.metrics.QuartzJobMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;

import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@link JobSchedulerPort} 的 Quartz 实现。
 *
 * <p>要点：
 * <ul>
 *   <li><b>延迟获取调度器</b>：采用 {@link ObjectProvider} 而非构造器直注，避免普通
 *       {@code @Component} 早于 {@code Scheduler} Bean 完成初始化；运行期按需判断 {@link #available()}；</li>
 *   <li><b>任务体</b>：统一调度 {@code SystemJob}，由它回调 {@link JobInvoker#run(Long)}，
 *       业务服务无需感知 Quartz；</li>
 *   <li><b>幂等注册</b>：同一引用重复注册时先删除旧 JobDetail/Trigger 再写入，
 *       编辑任务后重新调度不会抛 {@code ObjectAlreadyExistsException}；</li>
 *   <li><b>幂等取消</b>：引用不存在时静默忽略，仅记录告警日志。</li>
 * </ul>
 */
@Slf4j
@Component
public class QuartzSchedulerPort implements JobSchedulerPort {

    /** 自建任务的 Quartz 组名。 */
    public static final String JOB_GROUP = "sysjob";

    /** 周期任务引用前缀，与 {@code sys_job.scheduler_ref} 中持久化的值一致。 */
    private static final String RECURRING_PREFIX = "sysjob-";

    private final ObjectProvider<Scheduler> schedulerProvider;
    private final JobInvoker jobInvoker;
    private final ObjectProvider<QuartzJobMetrics> metricsProvider;

    public QuartzSchedulerPort(ObjectProvider<Scheduler> schedulerProvider,
                               JobInvoker jobInvoker,
                               ObjectProvider<QuartzJobMetrics> metricsProvider) {
        this.schedulerProvider = schedulerProvider;
        this.jobInvoker = jobInvoker;
        this.metricsProvider = metricsProvider;
    }

    @Override
    public boolean available() {
        return schedulerProvider.getIfAvailable() != null;
    }

    @Override
    public String scheduleCron(Long jobId, String cron) {
        Scheduler scheduler = requireScheduler();
        String ref = RECURRING_PREFIX + jobId;
        String quartzCron = QuartzCron.normalize(cron);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(ref, JOB_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(quartzCron))
                .build();
        replaceJob(scheduler, ref, trigger, jobId);
        log.info("已注册 Quartz CRON 周期任务 ref={} cron={} (quartz={}) jobId={}",
                ref, cron, quartzCron, jobId);
        return ref;
    }

    @Override
    public String scheduleInterval(Long jobId, Duration interval) {
        Scheduler scheduler = requireScheduler();
        String ref = RECURRING_PREFIX + jobId;
        long millis = Math.max(1000L, interval.toMillis());
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(ref, JOB_GROUP)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(millis)
                        .repeatForever())
                .build();
        replaceJob(scheduler, ref, trigger, jobId);
        log.info("已注册 Quartz 间隔周期任务 ref={} interval={}ms jobId={}", ref, millis, jobId);
        return ref;
    }

    @Override
    public String scheduleOneTime(Long jobId, Instant fireAt) {
        Scheduler scheduler = requireScheduler();
        String ref = UUID.randomUUID().toString();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(ref, JOB_GROUP)
                .startAt(Date.from(fireAt))
                .build();
        replaceJob(scheduler, ref, trigger, jobId);
        log.info("已注册 Quartz 一次性任务 ref={} fireAt={} jobId={}", ref, fireAt, jobId);
        return ref;
    }

    @Override
    public void cancelRecurring(String recurringRef) {
        cancel(recurringRef);
    }

    @Override
    public void cancelOneTime(String uuidRef) {
        cancel(uuidRef);
    }

    private void cancel(String ref) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return;
        }
        try {
            boolean removed = scheduler.deleteJob(JobKey.jobKey(ref, JOB_GROUP));
            if (removed) {
                QuartzJobMetrics metrics = metricsProvider.getIfAvailable();
                if (metrics != null) {
                    metrics.recordCancelled();
                }
                log.info("已取消 Quartz 任务 ref={}", ref);
            } else {
                // 已执行完毕 / 从未注册：视为幂等成功
                log.warn("取消 Quartz 任务时引用不存在（按幂等忽略）ref={}", ref);
            }
        } catch (SchedulerException | RuntimeException e) {
            log.warn("取消 Quartz 任务失败（按幂等忽略）ref={} err={}", ref, e.toString());
        }
    }

    private void replaceJob(Scheduler scheduler, String ref, Trigger trigger, Long jobId) {
        try {
            // 幂等：先删除同引用的旧任务（编辑任务后重新注册会走到这里）
            scheduler.deleteJob(JobKey.jobKey(ref, JOB_GROUP));
            JobDataMap data = new JobDataMap();
            data.put(SystemJob.KEY_JOB_ID, jobId);
            JobDetail detail = JobBuilder.newJob(SystemJob.class)
                    .withIdentity(ref, JOB_GROUP)
                    .setJobData(data)
                    .build();
            scheduler.scheduleJob(detail, trigger);
            QuartzJobMetrics metrics = metricsProvider.getIfAvailable();
            if (metrics != null) {
                metrics.recordScheduled();
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Quartz 任务注册失败 ref=" + ref, e);
        }
    }

    private Scheduler requireScheduler() {
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            throw new IllegalStateException("Quartz Scheduler 不可用，无法注册定时任务"
                    + "（请检查 app.quartz.enabled / job.config.QuartzConfig）");
        }
        return scheduler;
    }
}
