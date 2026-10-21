package com.acme.scaffold.observability.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.observability.dto.MetricView;
import com.acme.scaffold.observability.dto.SpanView;
import com.acme.scaffold.observability.dto.TraceSummaryView;
import com.acme.scaffold.observability.export.OtlpSpan;
import com.acme.scaffold.observability.export.SpanExporter;
import com.acme.scaffold.observability.metrics.MetricsRegistry;
import com.acme.scaffold.observability.recorder.SpanRecorder;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 可观测性运维服务：聚合本地 span 记录器（{@link SpanRecorder}）与 Micrometer 指标注册表
 * （{@link MetricsRegistry}），向 REST 层提供追踪回溯、运行态摘要与指标快照查询。
 *
 * <p>输入校验策略与其它运维模块一致：查询参数宽容归一（null/空白按缺省处理），
 * 显式非法值（limit 越界、status 非枚举、traceId 空串）抛
 * {@link BusinessException#badRequest(String)}，由全局异常处理器映射为 400 + {@code COMMON_001}。</p>
 */
@Service
@RequiredArgsConstructor
public class ObservabilityService {

    /** limit 缺省值：管理面板一页的常规容量。 */
    public static final int DEFAULT_LIMIT = 50;

    private static final List<String> STATUS_VALUES = List.of("UNSET", "OK", "ERROR");

    private final SpanRecorder recorder;
    private final SpanExporter exporter;
    private final MetricsRegistry metricsRegistry;

    /**
     * 查询最近记录的 span（最新在前），支持按状态与 traceId 过滤。
     *
     * @param limit    返回条数（null 取缺省 50；超出记录器容量抛 400）
     * @param status   状态过滤（null/空白不过滤；仅接受 UNSET/OK/ERROR，大小写不敏感）
     * @param traceId  traceId 过滤（null/空白不过滤）
     * @return span 视图列表（可能为空）
     */
    public List<SpanView> recentSpans(Integer limit, String status, String traceId) {
        int take = normalizeLimit(limit);
        String statusFilter = normalizeStatus(status);
        String traceFilter = normalizeTraceId(traceId);
        return recorder.recent(take).stream()
                .filter(s -> statusFilter == null || statusFilter.equals(s.status.name()))
                .filter(s -> traceFilter == null || traceFilter.equals(s.traceId))
                .map(this::toView)
                .toList();
    }

    /**
     * 按 traceId 检索同一追踪的全部 span（时间正序）。
     *
     * @param traceId 追踪标识（不可为空）
     * @return span 视图列表（无匹配时为空列表）
     */
    public List<SpanView> spansByTraceId(String traceId) {
        String normalized = normalizeTraceId(traceId);
        if (normalized == null) {
            throw BusinessException.badRequest("traceId 不能为空");
        }
        return recorder.byTraceId(normalized).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 清空本地 span 留存（不影响 OTLP 导出链路）。
     *
     * @return 清除前留存的条数
     */
    public int clearSpans() {
        return recorder.clear();
    }

    /**
     * 采集可观测性运行态摘要（记录器 + 导出器双链路）。
     *
     * @return 摘要视图
     */
    public TraceSummaryView summary() {
        SpanRecorder.RecorderStats stats = recorder.stats();
        return new TraceSummaryView(
                stats.enabled(), stats.capacity(), stats.size(),
                stats.okCount(), stats.errorCount(), stats.unsetCount(),
                exporter.isEnabled());
    }

    /**
     * 指标快照：按名称前缀过滤当前注册的 meter（默认全量）。
     *
     * @param prefix 名称前缀（null/空白表示不过滤）
     * @return 指标视图列表（名称字典序，便于前端稳定渲染）
     */
    public List<MetricView> metricSnapshot(String prefix) {
        String namePrefix = (prefix == null || prefix.isBlank()) ? null : prefix;
        List<MetricView> result = new ArrayList<>();
        for (Meter meter : metricsRegistry.raw().getMeters()) {
            String name = meter.getId().getName();
            if (namePrefix != null && !name.startsWith(namePrefix)) {
                continue;
            }
            result.add(new MetricView(
                    name,
                    meter.getId().getType().name(),
                    meter.getId().getDescription(),
                    meter.getId().getBaseUnit(),
                    toTagMap(meter.getId().getTags()),
                    firstValue(meter)));
        }
        result.sort((a, b) -> a.name().compareTo(b.name()));
        return result;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > recorder.capacity()) {
            throw BusinessException.badRequest(
                    "limit 须在 1 ~ " + recorder.capacity() + " 之间，当前=" + limit);
        }
        return limit;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_VALUES.contains(normalized)) {
            throw BusinessException.badRequest(
                    "status 仅支持 " + STATUS_VALUES + "，当前=" + status);
        }
        return normalized;
    }

    private String normalizeTraceId(String traceId) {
        return (traceId == null || traceId.isBlank()) ? null : traceId.trim();
    }

    private SpanView toView(OtlpSpan span) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (OtlpSpan.Attribute attribute : span.attributes) {
            attributes.put(attribute.key, attribute.value);
        }
        return new SpanView(
                span.traceId, span.spanId, span.parentSpanId, span.name,
                span.kind.name(), span.status.name(), span.statusMessage,
                span.startEpochNanos / 1_000_000L, span.durationNanos() / 1_000_000L,
                attributes);
    }

    private static Map<String, String> toTagMap(List<Tag> tags) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Tag tag : tags) {
            result.put(tag.getKey(), tag.getValue());
        }
        return result;
    }

    private static Double firstValue(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            return measurement.getValue();
        }
        return null;
    }
}
