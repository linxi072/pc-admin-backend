package com.acme.scaffold.job.handler;

import com.acme.scaffold.job.metrics.QuartzJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 内置周期任务（公告过期、监控采样、幂等清理、DemoJob）的 Quartz 执行入口。
 *
 * <p>按 JobDataMap 中的 {@code taskKey} 从 {@link BuiltinJobTasks} 取 Runnable 执行；
 * 依赖由 {@code AutowiringSpringBeanJobFactory} 注入，故本类无需注册为 {@code @Component}。
 */
@Slf4j
public class BuiltinQuartzJob implements Job {

    /** JobDataMap 键：内置任务标识。 */
    public static final String KEY_TASK = "taskKey";

    @Autowired
    private BuiltinJobTasks builtinJobTasks;

    @Autowired(required = false)
    private QuartzJobMetrics metrics;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String taskKey = context.getMergedJobDataMap().getString(KEY_TASK);
        Runnable task = builtinJobTasks == null ? null : builtinJobTasks.get(taskKey);
        if (task == null) {
            log.error("Quartz 内置任务未注册 taskKey={} key={}", taskKey, context.getJobDetail().getKey());
            if (metrics != null) {
                metrics.recordFailure();
            }
            return;
        }
        try {
            task.run();
            if (metrics != null) {
                metrics.recordSuccess();
            }
        } catch (Exception e) {
            if (metrics != null) {
                metrics.recordFailure();
            }
            log.error("Quartz 内置任务执行失败 taskKey={}", taskKey, e);
            throw new JobExecutionException("内置任务执行失败 taskKey=" + taskKey, e);
        }
    }
}
