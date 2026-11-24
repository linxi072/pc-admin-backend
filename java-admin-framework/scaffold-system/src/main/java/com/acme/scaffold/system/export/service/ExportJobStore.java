package com.acme.scaffold.system.export.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * 异步导出任务内存表（E5）。
 *
 * <p>采用 {@link ConcurrentHashMap} + 惰性过期清理（不新增定时线程池）：每次读写时清理超过 TTL 的任务。
 * 任务状态是短生命周期的临时数据，重启丢失可接受（前端按「任务不存在」提示重新提交），
 * 因此不引入 DDL / MyBatis 映射，保持零新增依赖与最小改动面。</p>
 */
@Component
public class ExportJobStore {

    /** 任务保留窗口：2 小时，覆盖大文件导出 + 用户晚下载的场景。 */
    private static final long TTL_MS = TimeUnit.HOURS.toMillis(2);

    private final ConcurrentHashMap<String, ExportJob> jobs = new ConcurrentHashMap<>();

    public void put(ExportJob job) {
        evictExpired();
        jobs.put(job.jobId(), job);
    }

    public ExportJob get(String jobId) {
        evictExpired();
        return jobs.get(jobId);
    }

    /** 原子更新；任务已被清理时返回 null。 */
    public ExportJob update(String jobId, UnaryOperator<ExportJob> updater) {
        return jobs.computeIfPresent(jobId, (key, current) -> updater.apply(current));
    }

    /** 当前留存任务数（供运维/测试断言）。 */
    public int size() {
        evictExpired();
        return jobs.size();
    }

    private void evictExpired() {
        long deadline = System.currentTimeMillis() - TTL_MS;
        jobs.entrySet().removeIf(e -> e.getValue().submittedAt() < deadline);
    }
}
