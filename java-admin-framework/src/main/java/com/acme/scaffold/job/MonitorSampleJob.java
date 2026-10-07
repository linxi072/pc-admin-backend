package com.acme.scaffold.job;

import com.acme.scaffold.monitor.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 监控指标采集周期任务。
 * <p>由 {@link com.acme.scaffold.config.JobRunrConfig} 注册为 JobRunr 周期任务
 * {@code monitor-sample-job}：每 1 分钟采集一次 CPU/内存/磁盘/JVM 与在线用户数落库，
 * 供「按时间范围查询」与趋势图使用，避免查询时实时计算带来的开销。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorSampleJob {

    private final MonitorService monitorService;

    /** 采集运行指标并落库。 */
    public void sample() {
        int inserted = monitorService.sampleOnce();
        if (inserted > 0) {
            log.debug("监控指标采样已落库 {} 条", inserted);
        }
    }
}