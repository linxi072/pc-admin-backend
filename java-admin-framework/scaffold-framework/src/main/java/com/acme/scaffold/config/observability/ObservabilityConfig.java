package com.acme.scaffold.config.observability;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpanExporter;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 可观测性与异步配置：审计日志异步线程池，以及指标公共标签与命名归一。
 */
@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfig {

    /** 审计日志异步写入线程池。 */
    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** 统一指标过滤：屏蔽 tomcat.* / flowable.* 等高频噪声指标，避免指标污染。
     *  作为 MeterFilter Bean，由 Spring Boot 自动应用到所有 MeterRegistry。 */
    @Bean
    public MeterFilter metricsFilter() {
        return MeterFilter.deny(id ->
                id.getName().startsWith("tomcat.") || id.getName().startsWith("flowable."));
    }

    /**
     * 零依赖 OTLP/HTTP span 导出器（A1）。默认关闭，由 observability.otlp.enabled 控制。
     *
     * <p>无条件注册（禁用时 {@code isEnabled()} 为 false，零副作用）：与 {@code SpanRecorder}
     * 并列构成 {@code SpanExporter} 的两个 sink，由 {@code OtlpTracingFilter} 经
     * {@code List<SpanExporter>} 按各自开关分发（双开关正交、互不影响）。</p>
     *
     * <p>标记 {@code @Primary}：使单点注入点（{@code ObservabilityService} 摘要中的
     * 「OTLP 导出链路」状态）在双 sink 并存时明确解析到本 bean，避免按类型注入歧义。</p>
     */
    @Bean
    @Primary
    public OtlpSpanExporter otlpSpanExporter(ObservabilityProperties props) {
        ObservabilityProperties.Otlp otlp = props.getOtlp();
        return new OtlpSpanExporter(otlp.isEnabled(), otlp.getEndpoint(),
                otlp.getServiceName(), otlp.getTimeoutMillis());
    }
}
