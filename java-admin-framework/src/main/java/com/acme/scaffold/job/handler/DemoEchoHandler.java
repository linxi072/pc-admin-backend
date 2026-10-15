package com.acme.scaffold.job.handler;

import com.acme.scaffold.job.domain.JobExecutionContext;
import com.acme.scaffold.job.domain.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示处理器：打印任务参数，用于验证「配置 → 启动 → 触发 → 日志」全链路。
 * <p>taskKey = {@code demo-echo}。可在任务参数中配置 {@code {"fail": true}} 以模拟执行失败，
 * 便于验证失败统计与错误信息展示。
 */
@Slf4j
@Component
public class DemoEchoHandler implements JobHandler {

    @Override
    public String key() {
        return "demo-echo";
    }

    @Override
    public String name() {
        return "演示-打印参数";
    }

    @Override
    public void execute(JobExecutionContext context) {
        log.info("[定时任务] demo-echo 触发 jobId={} jobName={} params={}",
                context.jobId(), context.jobName(), context.paramsRaw());
        if (context.paramsRaw() != null && context.paramsRaw().contains("\"fail\":true")) {
            throw new IllegalStateException("演示处理器：按参数要求模拟执行失败");
        }
    }
}
