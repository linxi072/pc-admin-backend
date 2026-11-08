package com.acme.scaffold.job.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示任务（迁移前为 XXL-JOB 的 demoJobHandler）。
 * <p>
 * Quartz 通过 BuiltinQuartzJob 调用 Spring Bean 的方法，无需独立调度中心服务；
 * 调度元数据保存在应用内嵌 Scheduler 的 RAMJobStore 中。
 */
@Slf4j
@Component
public class DemoJob {

    public void run() {
        log.info("Quartz 定时任务执行：{}", java.time.LocalDateTime.now());
    }
}
