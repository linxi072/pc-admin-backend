package com.acme.scaffold.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 可观测性与异步配置：审计日志异步线程池，以及指标公共标签与命名归一。
 */
@Configuration
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
}
