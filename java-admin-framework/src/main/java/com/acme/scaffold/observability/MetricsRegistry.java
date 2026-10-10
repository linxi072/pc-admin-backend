package com.acme.scaffold.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 统一指标桥接门面（F-12.6）。
 *
 * <p>封装 {@code io.micrometer.core.instrument.MeterRegistry}，为业务代码提供类型安全的
 * counter / timer / summary / gauge 构造方法，并强制统一的命名（{@code app.<domain>.<metric>}）
 * 与 HELP 描述。业务代码只依赖本门面，不直接 import MeterRegistry，从而：
 * <ul>
 *   <li>便于单测替换（构造时注入 {@code SimpleMeterRegistry} 即可断言计数/计时）；</li>
 *   <li>未来若切换采集后端（如 OTLP 直采）只需改此处，业务零改动；</li>
 *   <li>所有 meter 注册必带 description，Prometheus 抓取即有 HELP 文本，避免"无名指标"。</li>
 * </ul>
 *
 * <p>{@code MeterRegistry} 由 Spring Boot（actuator + micrometer-registry-prometheus）自动配置，
 * 本类以 {@code @Component} 注入该 bean，无需额外 @Configuration。
 * 指标噪声过滤（屏蔽 tomcat.* / flowable.*）由 {@code ObservabilityConfig#metricsFilter()} 的
 * MeterFilter 统一处理，与本门面正交、互不耦合。
 */
@Component
public class MetricsRegistry {

    private final MeterRegistry registry;

    public MetricsRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 计数器：累计次数类指标（如登录成功/失败、接口调用数）。 */
    public Counter counter(String name, String description, String... tags) {
        return Counter.builder(name)
                .description(description)
                .tags(toTags(tags))
                .register(registry);
    }

    /** 计时器：记录耗时分布（如审批处理时长、SQL 耗时）。 */
    public Timer timer(String name, String description, String... tags) {
        return Timer.builder(name)
                .description(description)
                .tags(toTags(tags))
                .register(registry);
    }

    /** 分布摘要：记录数值分布（如响应体大小、批量条数）。 */
    public DistributionSummary summary(String name, String description, String... tags) {
        return DistributionSummary.builder(name)
                .description(description)
                .tags(toTags(tags))
                .register(registry);
    }

    /** 仪表：采样瞬时值（如在线会话数、队列积压、线程数）。 */
    public <T> Gauge gauge(String name, String description, T stateObject,
                           ToDoubleFunction<T> valueFunction, String... tags) {
        return Gauge.builder(name, stateObject, valueFunction)
                .description(description)
                .tags(toTags(tags))
                .register(registry);
    }

    /** 暴露底层注册表，供需直接操作 MeterRegistry 的高级场景（如 MeterFilter 之外的批量注册）。 */
    public MeterRegistry raw() {
        return registry;
    }

    private static List<io.micrometer.core.instrument.Tag> toTags(String[] tags) {
        List<io.micrometer.core.instrument.Tag> result = new ArrayList<>();
        if (tags == null) {
            return result;
        }
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "tags 必须成对出现（key, value, key, value, ...），实际长度=" + tags.length);
        }
        for (int i = 0; i < tags.length; i += 2) {
            result.add(io.micrometer.core.instrument.Tag.of(tags[i], tags[i + 1]));
        }
        return result;
    }
}
