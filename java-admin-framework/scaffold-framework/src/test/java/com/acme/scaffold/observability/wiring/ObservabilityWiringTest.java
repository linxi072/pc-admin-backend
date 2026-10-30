package com.acme.scaffold.observability.wiring;

import com.acme.scaffold.config.observability.ObservabilityConfig;
import com.acme.scaffold.observability.export.SpanExporter;
import com.acme.scaffold.observability.metrics.MetricsRegistry;
import com.acme.scaffold.observability.recorder.SpanRecorder;
import com.acme.scaffold.observability.service.ObservabilityService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可观测性装配守护测试（真实联调回归）：{@code SpanRecorder} 与 {@code otlpSpanExporter}
 * 同为 {@link SpanExporter} sink 且并存注册，验证单点注入点 {@link ObservabilityService}
 * 经 {@code @Primary} 明确解析到 OTLP 导出链路，上下文可正常刷新。
 *
 * <p>背景：此前 {@code ObservabilityService} 按类型注入单个 {@code SpanExporter}，
 * 而 {@code ObservabilityConfig#otlpSpanExporter} 无条件注册，与 {@code SpanRecorder}
 * 并存时启动即报 {@code NoUniqueBeanDefinitionException}（local 默认组合
 * otlp=false + recorder=true 必现）。既有单测以 Mockito 手工构造，不经过 Spring
 * 装配解析，故未暴露——本测试以轻量 Spring 上下文补齐该盲区。</p>
 */
@DisplayName("Observability 装配守护：双 SpanExporter sink 并存")
class ObservabilityWiringTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private AnnotationConfigApplicationContext startContext(boolean otlpEnabled, boolean recorderEnabled) {
        Map<String, Object> props = new HashMap<>();
        props.put("observability.otlp.enabled", String.valueOf(otlpEnabled));
        props.put("observability.recorder.enabled", String.valueOf(recorderEnabled));
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-observability", props));
        context.getBeanFactory().registerSingleton("meterRegistry", new SimpleMeterRegistry());
        context.register(ObservabilityConfig.class, SpanRecorder.class, ObservabilityService.class, MetricsRegistry.class);
        context.refresh();
        return context;
    }

    @Test
    @DisplayName("复现组合：otlp=false + recorder=true，双 sink 并存且 exporter 解析为 OTLP 链路")
    void dualSinks_defaultLocalCombination_resolvesPrimaryOtlp() {
        AnnotationConfigApplicationContext ctx = startContext(false, true);

        // 两个 SpanExporter bean 并存（sink 聚合设计），集合注入方 OtlpTracingFilter 不受影响
        assertEquals(2, ctx.getBeansOfType(SpanExporter.class).size());

        // 单点注入方 ObservabilityService 经 @Primary 解析到 otlpSpanExporter（isEnabled=false），
        // 而非 SpanRecorder（isEnabled=true）——摘要中两条链路状态必须与配置一一对应
        ObservabilityService service = ctx.getBean(ObservabilityService.class);
        assertFalse(service.summary().exporterEnabled());
        assertTrue(service.summary().recorderEnabled());
    }

    @Test
    @DisplayName("双开组合：otlp=true + recorder=true，两条链路均启用且装配成功")
    void bothSinksEnabled_wiringResolves() {
        AnnotationConfigApplicationContext ctx = startContext(true, true);

        assertEquals(2, ctx.getBeansOfType(SpanExporter.class).size());
        ObservabilityService service = ctx.getBean(ObservabilityService.class);
        assertTrue(service.summary().exporterEnabled());
        assertTrue(service.summary().recorderEnabled());
    }

    @Test
    @DisplayName("全关组合：otlp=false + recorder=false，上下文仍可刷新且两条链路均报禁用")
    void bothSinksDisabled_wiringResolves() {
        AnnotationConfigApplicationContext ctx = startContext(false, false);

        ObservabilityService service = ctx.getBean(ObservabilityService.class);
        assertFalse(service.summary().exporterEnabled());
        assertFalse(service.summary().recorderEnabled());
    }
}
