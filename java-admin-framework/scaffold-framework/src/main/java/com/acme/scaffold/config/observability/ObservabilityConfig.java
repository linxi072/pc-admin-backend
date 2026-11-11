package com.acme.scaffold.config.observability;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpanExporter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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

    /**
     * 通知重试退避调度器（F2）：替代 {@code NotificationService} 内联的
     * {@code Thread.sleep} 阻塞式退避。退避经本托管调度线程池计时，调用线程在
     * {@code ScheduledFuture#get()} 上同步等待——语义与阻塞退避一致（仍同步返回投递结果），
     * 但等待由专用线程池管理，避免直接占用业务/请求线程做无意义休眠。
     */
    @Bean("notificationRetryScheduler")
    public TaskScheduler notificationRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("notify-retry-");
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * 通知异步投递线程池（P0-3）：与业务/请求线程隔离，承接 {@code NotificationService.dispatchAsync}
     * 的实际投递与重试退避等待，使调用方（典型为 HTTP 请求线程）不被 {@code .get()} 同步阻塞。
     * 有界队列 + CallerRunsPolicy 兜底，避免异步任务无限堆积压垮内存。
     */
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * 导出线程池（P1-5）：与请求/业务线程隔离，承接大结果集导出的查询与字节生成，
     * 避免占用 HTTP 请求线程做长时间 IO / 物化（F3 堆内行数上限之外的时间维隔离）。
     * 有界队列 + CallerRunsPolicy：队列满时由调用方线程兜底执行，避免任务丢失或无限堆积。
     * 控制器可改用 {@code ExportCenterService#downloadAsync} 离场到本池。
     */
    @Bean("exportExecutor")
    public Executor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
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
