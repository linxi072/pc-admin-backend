package com.acme.scaffold.consistency;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ObservabilityProperties} 与 {@code application.yml} 的一致性测试。
 *
 * <p>以 Spring Boot 原生 {@link YamlPropertySourceLoader} 从 classpath 真实加载
 * {@code application.yml}（仅 YAML → PropertySource 解析，不触发 ${} 占位替换），
 * 再将其挂入 {@link StandardEnvironment} 并用 {@link Binder} 绑定 {@code observability} 前缀，守护：</p>
 * <ul>
 *   <li>yml 的 recorder 段键名（kebab-case）与属性类 setter 正确映射；</li>
 *   <li>属性类默认值与 yml 显式值一致（{@code enabled=true / capacity=200}）；</li>
 *   <li>otlp 既有键不受影响（回归）。</li>
 * </ul>
 */
@DisplayName("ObservabilityProperties 与 application.yml 一致性测试")
class ObservabilityPropertiesTest {

    private static ObservabilityProperties loadFromYaml() throws IOException {
        Resource resource = new ClassPathResource("application.yml");
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", resource);
        assertFalse(sources.isEmpty(), "application.yml 应至少产出一个 PropertySource");
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addFirst);
        Binder binder = Binder.get(environment);
        return binder.bind("observability", Bindable.of(ObservabilityProperties.class)).get();
    }

    @Test
    @DisplayName("yml 的 observability.recorder 段正确绑定（enabled=true / capacity=200）")
    void bindsRecorderFromYaml() throws Exception {
        ObservabilityProperties props = loadFromYaml();
        assertTrue(props.getRecorder().isEnabled());
        assertEquals(200, props.getRecorder().getCapacity());
    }

    @Test
    @DisplayName("yml 的 observability.otlp 段不受影响（回归：默认关闭）")
    void bindsOtlpFromYaml() throws Exception {
        ObservabilityProperties props = loadFromYaml();
        assertFalse(props.getOtlp().isEnabled());
    }

    @Test
    @DisplayName("属性类默认值与 yml 显式值一致")
    void defaultsMatchYaml() throws Exception {
        ObservabilityProperties defaults = new ObservabilityProperties();
        ObservabilityProperties fromYaml = loadFromYaml();
        assertEquals(defaults.getRecorder().isEnabled(), fromYaml.getRecorder().isEnabled());
        assertEquals(defaults.getRecorder().getCapacity(), fromYaml.getRecorder().getCapacity());
    }
}
