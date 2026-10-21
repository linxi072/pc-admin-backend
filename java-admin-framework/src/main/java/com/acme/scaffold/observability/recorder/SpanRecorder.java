package com.acme.scaffold.observability.recorder;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import com.acme.scaffold.observability.export.SpanExporter;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * 请求级 span 本地记录器（有界内存环形缓冲），实现 {@link SpanExporter}——
 * 即「本地留存」作为一种 span sink，与 OTLP Collector 导出器并列装配，
 * 由 {@code OtlpTracingFilter} 经 {@code List<SpanExporter>} 统一分发（二者开关正交、互不影响）。
 *
 * <p>价值：Collector 未部署 / {@code observability.otlp.enabled=false} 时的本地回溯
 * （"这个 traceId 刚才经过了哪些请求、耗时与状态"），并通过 {@code ObservabilityController}
 * 暴露管理查询。容量有界（默认 200 条），超出后按入队序淘汰最旧记录，内存占用恒定。</p>
 *
 * <p>线程安全：所有读写经同一监视器锁串行化；span 生产速率低（每请求 1 条），锁竞争可忽略。
 * 组件本身无外部依赖（不触网、不落库），禁用时 {@link #isEnabled()} 为 false，过滤器据此跳过收集。</p>
 */
@Component
public class SpanRecorder implements SpanExporter {

    private final boolean enabled;
    private final int capacity;

    /** 入队序（旧 → 新）的 span 环形缓冲；由锁保护。 */
    private final Deque<OtlpSpan> buffer = new ArrayDeque<>();
    private final LongAdder okCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final LongAdder unsetCount = new LongAdder();

    public SpanRecorder(ObservabilityProperties properties) {
        ObservabilityProperties.Recorder recorder = properties.getRecorder();
        this.enabled = recorder.isEnabled();
        this.capacity = Math.max(1, recorder.getCapacity());
    }

    /** @return 记录器是否启用（由 {@code observability.recorder.enabled} 控制） */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** @return 缓冲容量上限（至少 1） */
    public int capacity() {
        return capacity;
    }

    /** @return 当前留存条数 */
    public int size() {
        synchronized (buffer) {
            return buffer.size();
        }
    }

    /**
     * {@link SpanExporter} 适配：将一批 span 逐条录入本地缓冲。
     * 未启用或入参为空时静默忽略（绝不抛异常影响主请求链路）。
     */
    @Override
    public void export(List<OtlpSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        spans.forEach(this::record);
    }

    /**
     * 记录一条 span。未启用或入参为空时静默忽略（绝不抛异常影响主请求链路）。
     * 超出容量时淘汰最旧记录；同时按状态累计计数。
     */
    public void record(OtlpSpan span) {
        if (!enabled || span == null) {
            return;
        }
        synchronized (buffer) {
            buffer.addLast(span);
            while (buffer.size() > capacity) {
                buffer.pollFirst();
            }
        }
        switch (span.status) {
            case OK -> okCount.increment();
            case ERROR -> errorCount.increment();
            default -> unsetCount.increment();
        }
    }

    /**
     * 查询最近记录的 span，按入队时间倒序（最新在前）。
     *
     * @param limit 期望条数（至少 1，大于容量时按容量截断）
     * @return 只读副本（空列表表示无记录）
     */
    public List<OtlpSpan> recent(int limit) {
        int take = Math.max(1, Math.min(limit, capacity));
        synchronized (buffer) {
            List<OtlpSpan> snapshot = new ArrayList<>(buffer);
            // 倒序取最新 take 条
            int from = Math.max(0, snapshot.size() - take);
            List<OtlpSpan> result = new ArrayList<>(snapshot.subList(from, snapshot.size()));
            Collections.reverse(result);
            return List.copyOf(result);
        }
    }

    /**
     * 按 traceId 检索同一追踪的全部 span，保持入队序（时间正序）。
     *
     * @param traceId 追踪标识（调用方负责非空校验）
     * @return 匹配 span（空列表表示无匹配）
     */
    public List<OtlpSpan> byTraceId(String traceId) {
        synchronized (buffer) {
            List<OtlpSpan> result = new ArrayList<>();
            for (OtlpSpan span : buffer) {
                if (traceId.equals(span.traceId)) {
                    result.add(span);
                }
            }
            return List.copyOf(result);
        }
    }

    /**
     * 清空缓冲并重置计数。
     *
     * @return 清除前留存的条数
     */
    public int clear() {
        synchronized (buffer) {
            int size = buffer.size();
            buffer.clear();
            okCount.reset();
            errorCount.reset();
            unsetCount.reset();
            return size;
        }
    }

    /** @return 运行态统计快照（启用开关/容量/留存数/按状态累计数） */
    public RecorderStats stats() {
        synchronized (buffer) {
            return new RecorderStats(enabled, capacity, buffer.size(),
                    okCount.sum(), errorCount.sum(), unsetCount.sum());
        }
    }

    /**
     * 记录器统计快照。纯数据载体，与 OTLP {@link OtlpSpan.StatusCode} 三态对齐。
     *
     * @param enabled    是否启用
     * @param capacity   容量上限
     * @param size       当前留存条数
     * @param okCount    历史累计 OK 条数（含已被淘汰的）
     * @param errorCount 历史累计 ERROR 条数（含已被淘汰的）
     * @param unsetCount 历史累计 UNSET 条数（含已被淘汰的）
     */
    public record RecorderStats(boolean enabled, int capacity, int size,
                                long okCount, long errorCount, long unsetCount) {
    }
}
