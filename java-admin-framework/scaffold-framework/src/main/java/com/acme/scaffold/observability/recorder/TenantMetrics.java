package com.acme.scaffold.observability.recorder;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import com.acme.scaffold.observability.export.SpanExporter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 租户维度请求聚合（R14 租户维度）：作为 {@link SpanExporter} sink 之一，
 * 由 {@code OtlpTracingFilter} 在请求级 span 产出后统一分发（与本地留存、OTLP 导出并列，开关正交）。
 *
 * <p>价值：在既有「实例级」指标（CPU/内存/缓存命中/连接池）之外，补充「租户级」视角——
 * 多租户场景下，哪个租户请求量高、错误率高、P 时延大，是 APM 大盘的关键下钻维度。
 * 数据仅驻留内存、有界（默认 256 租户），恒定占用，不落库、不触网。</p>
 *
 * <p>线程安全：所有读写经同一监视器锁串行化；span 生产速率低（每请求至多 1 条），锁竞争可忽略。
 * 无租户上下文（{@code tenantId == null}）的 span（系统/内部调用）直接跳过，不污染租户视图。</p>
 */
@Component
public class TenantMetrics implements SpanExporter {

    private final boolean enabled;
    private final int maxTenants;

    /** 租户维度聚合（插入序 LinkedHashMap，用于超出上限时淘汰最早观测到的租户）。 */
    private final Map<Long, PerTenant> byTenant = new LinkedHashMap<>();

    public TenantMetrics(ObservabilityProperties properties) {
        ObservabilityProperties.TenantMetrics tm = properties.getTenantMetrics();
        this.enabled = tm.isEnabled();
        this.maxTenants = Math.max(1, tm.getMaxTenants());
    }

    /** @return 是否启用（由 {@code observability.tenant-metrics.enabled} 控制） */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * {@link SpanExporter} 适配：逐条聚合（无租户上下文的 span 跳过）。
     * 未启用或入参为空时静默忽略（绝不抛异常影响主请求链路）。
     */
    @Override
    public void export(List<OtlpSpan> spans) {
        if (!enabled || spans == null || spans.isEmpty()) {
            return;
        }
        for (OtlpSpan span : spans) {
            if (span.tenantId == null) {
                continue;
            }
            record(span.tenantId, span.status, span.durationNanos());
        }
    }

    private void record(long tenantId, OtlpSpan.StatusCode status, long durationNanos) {
        synchronized (byTenant) {
            PerTenant t = byTenant.computeIfAbsent(tenantId, k -> new PerTenant());
            t.requestCount.increment();
            if (status == OtlpSpan.StatusCode.ERROR) {
                t.errorCount.increment();
            }
            t.totalDurationNanos.add(durationNanos);
            t.lastSeenEpochMs = System.currentTimeMillis();
            if (byTenant.size() > maxTenants) {
                Iterator<Long> it = byTenant.keySet().iterator();
                if (it.hasNext()) {
                    byTenant.remove(it.next());
                }
            }
        }
    }

    /**
     * 租户维度聚合快照，按请求量降序（热点租户在前），便于 APM 大盘直接取 Top N。
     *
     * @return 快照列表（空列表表示尚无租户级观测）
     */
    public List<TenantMetricSnapshot> snapshot() {
        synchronized (byTenant) {
            List<TenantMetricSnapshot> list = new ArrayList<>(byTenant.size());
            for (Map.Entry<Long, PerTenant> entry : byTenant.entrySet()) {
                PerTenant t = entry.getValue();
                long requests = t.requestCount.sum();
                long errors = t.errorCount.sum();
                long totalNanos = t.totalDurationNanos.sum();
                double avgMs = requests == 0 ? 0d : (totalNanos / (double) requests) / 1_000_000d;
                double errorRate = requests == 0 ? 0d : errors / (double) requests;
                list.add(new TenantMetricSnapshot(entry.getKey(), requests, errors,
                        avgMs, errorRate, t.lastSeenEpochMs));
            }
            list.sort((a, b) -> Long.compare(b.requestCount(), a.requestCount()));
            return List.copyOf(list);
        }
    }

    /**
     * 清空全部租户聚合。
     *
     * @return 清除前留存的租户数
     */
    public int clear() {
        synchronized (byTenant) {
            int size = byTenant.size();
            byTenant.clear();
            return size;
        }
    }

    /** 是否启用（运行态可观测性摘要复用）。 */
    public boolean tenantMetricsEnabled() {
        return enabled;
    }

    /** 单租户聚合载体：纯计数 + LongAdder，避免并发写竞争。 */
    private static final class PerTenant {
        final LongAdder requestCount = new LongAdder();
        final LongAdder errorCount = new LongAdder();
        final LongAdder totalDurationNanos = new LongAdder();
        volatile long lastSeenEpochMs = 0L;
    }

    /**
     * 租户维度聚合快照。纯数据载体，请求量降序。
     *
     * @param tenantId          租户 ID
     * @param requestCount     累计请求数
     * @param errorCount       累计错误数（status=ERROR）
     * @param avgDurationMillis 平均耗时（毫秒）
     * @param errorRate        错误率（0~1）
     * @param lastSeenEpochMs  最近一次观测到的 epoch 毫秒
     */
    public record TenantMetricSnapshot(long tenantId, long requestCount, long errorCount,
                                       double avgDurationMillis, double errorRate,
                                       long lastSeenEpochMs) {
    }
}
