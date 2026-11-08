package com.acme.scaffold.job.config;

import com.acme.scaffold.job.domain.QuartzCron;
import com.acme.scaffold.job.handler.AnnouncementExpiryJob;
import com.acme.scaffold.job.handler.BuiltinJobTasks;
import com.acme.scaffold.job.handler.BuiltinQuartzJob;
import com.acme.scaffold.job.handler.DemoJob;
import com.acme.scaffold.job.handler.IdempotencyCleanupJob;
import com.acme.scaffold.job.handler.MonitorSampleJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Quartz 调度装配与内置周期任务注册。
 *
 * <p>替代原 {@code JobRunrConfig}：不再依赖 Spring Boot Starter / SQL 存储 / 独立 Dashboard，
 * 改为应用内嵌 {@link Scheduler} + RAMJobStore + {@link java.util.concurrent} 线程池：
 * <ul>
 *   <li>{@code app.quartz.enabled=false} 时不创建 Scheduler，业务服务降级为「仅落库、不调度」；</li>
 *   <li>{@code app.quartz.thread-count} 控制工作线程数（默认 4，与原 JobRunr worker-count 一致）；</li>
 *   <li>Job 实例由 {@link AutowiringSpringBeanJobFactory} 注入 Spring Bean；</li>
 *   <li>4 个内置周期任务的 cron 仍沿用业务侧 5 段写法，注册时经
 *       {@link QuartzCron#normalize(String)} 转成 Quartz 6 段。</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class QuartzConfig implements ApplicationRunner {

    /** 内置任务组名，与用户自建任务（sysjob 组）隔离。 */
    private static final String BUILTIN_GROUP = "builtin";

    private static final String DEMO_JOB_ID = "demo-job";
    private static final String DEMO_JOB_CRON = "*/5 * * * *";

    private static final String ANNOUNCEMENT_EXPIRY_JOB_ID = "announcement-expiry-job";
    private static final String ANNOUNCEMENT_EXPIRY_JOB_CRON = "*/5 * * * *";

    private static final String MONITOR_SAMPLE_JOB_ID = "monitor-sample-job";
    private static final String MONITOR_SAMPLE_JOB_CRON = "* * * * *";

    private static final String IDEMPOTENCY_CLEANUP_JOB_ID = "idempotency-cleanup-job";
    private static final String IDEMPOTENCY_CLEANUP_JOB_CRON = "0 * * * *";

    private final ObjectProvider<Scheduler> schedulerProvider;
    private final BuiltinJobTasks builtinJobTasks;
    private final DemoJob demoJob;
    private final AnnouncementExpiryJob announcementExpiryJob;
    private final MonitorSampleJob monitorSampleJob;
    private final IdempotencyCleanupJob idempotencyCleanupJob;

    @Value("${app.quartz.thread-count:4}")
    private int threadCount;

    /** JobFactory 作为 Bean 返回，Spring 会回调 {@code ApplicationContextAware} 注入容器。 */
    @Bean
    public JobFactory quartzJobFactory() {
        return new AutowiringSpringBeanJobFactory();
    }

    /**
     * 应用内嵌调度器：RAMJobStore 不产生 QRTZ_* 表，也不需要独立数据源，
     * 与「移除 Flyway、由 DBA 手工初始化」的部署模型一致。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.quartz", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Scheduler quartzScheduler(JobFactory quartzJobFactory) throws Exception {
        Properties props = new Properties();
        props.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "ScaffoldScheduler");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", String.valueOf(Math.max(1, threadCount)));
        props.setProperty("org.quartz.threadPool.threadPriority", "5");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

        StdSchedulerFactory factory = new StdSchedulerFactory();
        factory.initialize(props);
        Scheduler scheduler = factory.getScheduler();
        scheduler.setJobFactory(quartzJobFactory);
        scheduler.start();
        log.info("Quartz Scheduler 已启动（RAMJobStore，工作线程 {}）", Math.max(1, threadCount));
        return scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            log.warn("Quartz Scheduler 不可用，跳过内置周期任务注册（app.quartz.enabled=false？）");
            return;
        }
        register(scheduler, DEMO_JOB_ID, DEMO_JOB_CRON, demoJob::run);
        register(scheduler, ANNOUNCEMENT_EXPIRY_JOB_ID, ANNOUNCEMENT_EXPIRY_JOB_CRON,
                announcementExpiryJob::offlineExpired);
        register(scheduler, MONITOR_SAMPLE_JOB_ID, MONITOR_SAMPLE_JOB_CRON,
                monitorSampleJob::sample);
        register(scheduler, IDEMPOTENCY_CLEANUP_JOB_ID, IDEMPOTENCY_CLEANUP_JOB_CRON,
                idempotencyCleanupJob::purgeExpired);
    }

    private void register(Scheduler scheduler, String id, String cron, Runnable task) {
        builtinJobTasks.register(id, task);
        String quartzCron = QuartzCron.normalize(cron);
        try {
            JobKey key = JobKey.jobKey(id, BUILTIN_GROUP);
            scheduler.deleteJob(key); // 幂等：重复启动不残留旧触发器
            JobDataMap data = new JobDataMap();
            data.put(BuiltinQuartzJob.KEY_TASK, id);
            JobDetail detail = JobBuilder.newJob(BuiltinQuartzJob.class)
                    .withIdentity(key)
                    .setJobData(data)
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(id, BUILTIN_GROUP)
                    .withSchedule(CronScheduleBuilder.cronSchedule(quartzCron))
                    .build();
            scheduler.scheduleJob(detail, trigger);
            log.info("已注册 Quartz 内置周期任务 id={} cron={} (quartz={})", id, cron, quartzCron);
        } catch (Exception e) {
            log.error("注册 Quartz 内置周期任务失败 id={} cron={}", id, cron, e);
        }
    }
}
