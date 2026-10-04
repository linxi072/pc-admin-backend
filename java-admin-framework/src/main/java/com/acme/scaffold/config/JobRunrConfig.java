package com.acme.scaffold.config;

import com.acme.scaffold.job.DemoJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

/**
 * JobRunr 配置（替代原 XxlJobConfig）。
 * <p>
 * JobRunr Spring Boot Starter 已完成核心装配：
 * <ul>
 *   <li>存储：SQL（复用业务 DataSource），启动时自动创建 jobrunr_* 表，无需 Redis / Docker；</li>
 *   <li>执行：应用内嵌 BackgroundJobServer，无需独立调度中心；</li>
 *   <li>可观测：Dashboard（默认仅 local 环境开启）。</li>
 * </ul>
 * 本类仅负责注册周期性任务。迁移前 XXL-JOB 的 {@code demoJobHandler} 对应此处的 {@code demo-job}。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JobRunrConfig implements ApplicationRunner {

    /** 每 5 分钟执行一次。 */
    private static final String DEMO_JOB_ID = "demo-job";
    private static final String DEMO_JOB_CRON = "*/5 * * * *";

    /**
     * 采用 ObjectProvider 懒获取，而非构造器直注 + {@code @ConditionalOnBean}。
     * 原因：普通 {@code @Configuration} 先于自动配置解析，{@code @ConditionalOnBean(JobScheduler.class)}
     * 在自动配置产出该 Bean 之前求值，恒为 false，会导致周期任务静默不注册。
     */
    private final ObjectProvider<JobScheduler> jobSchedulerProvider;
    private final DemoJob demoJob;

    @Override
    public void run(ApplicationArguments args) {
        JobScheduler jobScheduler = jobSchedulerProvider.getIfAvailable();
        if (jobScheduler == null) {
            log.warn("JobScheduler 不可用，跳过周期任务注册（请检查 jobrunr.job-scheduler.enabled）");
            return;
        }
        jobScheduler.scheduleRecurrently(DEMO_JOB_ID, DEMO_JOB_CRON, demoJob::run);
        log.info("已注册 JobRunr 周期任务 id={} cron={}", DEMO_JOB_ID, DEMO_JOB_CRON);
    }
}
