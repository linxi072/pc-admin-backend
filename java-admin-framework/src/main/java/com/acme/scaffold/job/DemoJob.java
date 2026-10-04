package com.acme.scaffold.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示任务（迁移前为 XXL-JOB 的 demoJobHandler）。
 * <p>
 * JobRunr 直接调度 Spring Bean 的方法，无需实现任何框架接口，也无需独立的调度中心服务：
 * 任务元数据持久化在业务库（jobrunr_* 表），由应用内嵌的 BackgroundJobServer 执行。
 */
@Slf4j
@Component
public class DemoJob {

    public void run() {
        log.info("JobRunr 定时任务执行：{}", java.time.LocalDateTime.now());
    }
}
