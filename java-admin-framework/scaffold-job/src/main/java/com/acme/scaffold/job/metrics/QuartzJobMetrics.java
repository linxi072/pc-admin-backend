package com.acme.scaffold.job.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Quartz 调度指标（替代原 JobRunr 未启用的 {@code jobrunr_jobs_*} 指标）。
 *
 * <p>使用 Spring Boot actuator 自动装配的 {@link MeterRegistry}；未启用 actuator 时回落到
 * {@link SimpleMeterRegistry}，保证业务代码无需判空、也不会因缺少 Bean 而启动失败。
 * 指标名遵循 Micrometer 点号命名，Prometheus 抓取后分别为
 * {@code quartz_jobs_succeeded_total} / {@code quartz_jobs_failed_total} /
 * {@code quartz_jobs_scheduled_total} / {@code quartz_jobs_cancelled_total}。
 */
@Component
public class QuartzJobMetrics {

    private final Counter succeeded;
    private final Counter failed;
    private final Counter scheduled;
    private final Counter cancelled;

    public QuartzJobMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        MeterRegistry target = registry == null ? new SimpleMeterRegistry() : registry;
        this.succeeded = counter(target, "quartz.jobs.succeeded", "Quartz 任务执行成功次数");
        this.failed = counter(target, "quartz.jobs.failed", "Quartz 任务执行失败次数");
        this.scheduled = counter(target, "quartz.jobs.scheduled", "Quartz 任务注册次数");
        this.cancelled = counter(target, "quartz.jobs.cancelled", "Quartz 任务取消次数");
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    public void recordSuccess() {
        succeeded.increment();
    }

    public void recordFailure() {
        failed.increment();
    }

    public void recordScheduled() {
        scheduled.increment();
    }

    public void recordCancelled() {
        cancelled.increment();
    }
}
