package com.acme.scaffold.job.port;

import com.acme.scaffold.job.metrics.QuartzJobMetrics;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 「定时任务管理」中用户自建任务的 Quartz 执行入口。
 *
 * <p>实例由 {@code AutowiringSpringBeanJobFactory} 创建并交给 Spring 注入依赖，
 * 不注册为 {@code @Component}（避免被容器提前实例化）。任务主键从 JobDataMap 读取，
 * 统一回调 {@link JobInvoker#run(Long)}，与调度引擎解耦。
 */
@Slf4j
public class SystemJob implements Job {

    /** JobDataMap 键：业务任务主键。 */
    public static final String KEY_JOB_ID = "jobId";

    @Autowired
    private JobInvoker jobInvoker;

    @Autowired(required = false)
    private QuartzJobMetrics metrics;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Object raw = context.getMergedJobDataMap().get(KEY_JOB_ID);
        Long jobId = raw instanceof Number number ? number.longValue() : null;
        if (jobId == null) {
            log.warn("Quartz 任务缺少 jobId，跳过本次触发 key={}", context.getJobDetail().getKey());
            if (metrics != null) {
                metrics.recordFailure();
            }
            return;
        }
        try {
            jobInvoker.run(jobId);
            if (metrics != null) {
                metrics.recordSuccess();
            }
        } catch (Exception e) {
            if (metrics != null) {
                metrics.recordFailure();
            }
            log.error("Quartz 任务执行失败 jobId={} key={}", jobId, context.getJobDetail().getKey(), e);
            throw new JobExecutionException("任务执行失败 jobId=" + jobId, e);
        }
    }
}
