package com.acme.scaffold.job.handler;

import com.acme.scaffold.job.domain.JobExecutionContext;
import com.acme.scaffold.job.domain.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;

/**
 * 演示处理器：输出当前时间与 JVM 概要信息（堆内存 / 线程数），用于验证任务可持续运行。
 * <p>taskKey = {@code demo-sysinfo}。
 */
@Slf4j
@Component
public class DemoSysInfoHandler implements JobHandler {

    @Override
    public String key() {
        return "demo-sysinfo";
    }

    @Override
    public String name() {
        return "演示-系统信息";
    }

    @Override
    public void execute(JobExecutionContext context) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        log.info("[定时任务] demo-sysinfo 触发 jobId={} time={} heap={}MB/{}MB threads={}",
                context.jobId(), LocalDateTime.now(), usedMb, maxMb, threads);
    }
}
