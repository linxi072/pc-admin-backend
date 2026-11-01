package com.acme.scaffold.monitor.observability;

import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.observability.metrics.MetricsRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * N+1 疑似事件 → Micrometer 指标桥接（nplus1-observability §7「未来方向」）。
 *
 * <p>将 {@link NPlusOneSuspectRegistry} 的运行态数据以 gauge 形式注册进 {@link MetricsRegistry}，
 * 使「N+1 疑似累计次数 / 当前留存条数 / 留存容量」可被 Prometheus 经 actuator 抓取，
 * 弥补「疑似事件仅留存进程内存、经 REST 暴露」的不足（多实例部署时各实例数据独立、需逐实例排查）。
 *
 * <p>本桥接为<b>纯增量</b>：不读取、不修改疑似事件留存，仅经 {@link NPlusOneSuspectRegistry} 的
 * 线程安全方法实时采样瞬时值；指标命名遵循 {@code app.monitor.nplus1.<metric>} 约定并带 HELP 描述。
 * 注册在构造期单次完成，gauge 借由 registry 自身方法惰性采样，无需定时刷新。</p>
 */
@Component
@RequiredArgsConstructor
public class NPlusOneMetricsBridge {

    private final MetricsRegistry metrics;
    private final NPlusOneSuspectRegistry registry;

    /** 注册 N+1 相关 gauge。幂等：重复调用会触发 Micrometer 同名 meter 合并（同 name+tags 取首个注册值）。 */
    public void register() {
        metrics.gauge("app.monitor.nplus1.total", "自进程启动以来 N+1 疑似越限累计次数（单调递增）",
                registry, r -> (double) r.total());
        metrics.gauge("app.monitor.nplus1.retained", "当前留存的 N+1 疑似事件条数（有界环形缓冲）",
                registry, r -> (double) r.size());
        metrics.gauge("app.monitor.nplus1.capacity", "N+1 疑似事件留存容量上限",
                registry, r -> (double) r.capacity());
    }
}
