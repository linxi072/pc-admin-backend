package com.acme.scaffold.config.center;

import com.acme.scaffold.common.event.DomainEvent;
import com.acme.scaffold.common.event.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCenterAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigCenterAutoConfiguration.class))
            .withUserConfiguration(EventPublisherConfig.class);

    @Test
    void beansRegisteredWhenEnabled() {
        runner.withPropertyValues(
                        "app.config-center.enabled=true",
                        "app.config-center.type=local")
                .run(ctx -> {
                    assertTrue(ctx.containsBean("configCenterPort"));
                    assertTrue(ctx.containsBean("configCenterService"));
                    assertNotNull(ctx.getBean(ConfigCenterPort.class));
                    assertNotNull(ctx.getBean(ConfigCenterService.class));
                    // 本地适配器类型为 local
                    assertTrue(ctx.getBean(ConfigCenterPort.class) instanceof LocalConfigCenterAdapter);
                });
    }

    @Test
    void noBeansWhenDisabled() {
        // 默认 enabled=false：不装配任何 R16 Bean，与既有行为零回归
        runner.run(ctx -> {
            assertFalse(ctx.containsBean("configCenterPort"));
            assertFalse(ctx.containsBean("configCenterService"));
        });
    }

    @Configuration
    static class EventPublisherConfig {
        @Bean
        EventPublisher eventPublisher() {
            return new EventPublisher() {
                @Override
                public void publish(DomainEvent event) {
                }
                @Override
                public void publishAll(List<? extends DomainEvent> events) {
                }
            };
        }
    }
}
