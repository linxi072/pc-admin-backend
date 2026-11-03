package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.controller.ChaosController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChaosAutoConfiguration} 条件装配测试：验证 {@code app.chaos.enabled} 门控。
 */
@DisplayName("ChaosAutoConfiguration 条件装配测试")
class ChaosAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChaosAutoConfiguration.class))
            .withUserConfiguration(ChaosController.class);

    @Test
    @DisplayName("enabled=true：注册 registry / filter / controller 三个 Bean")
    void beansRegisteredWhenEnabled() {
        runner.withPropertyValues("app.chaos.enabled=true")
                .run(ctx -> {
                    assertTrue(ctx.containsBean("chaosRegistry"));
                    assertTrue(ctx.containsBean("chaosService"));
                    assertTrue(ctx.containsBean("chaosFilter"));
                    assertTrue(ctx.containsBean("chaosController"));
                    assertNotNull(ctx.getBean(ChaosRegistry.class));
                    assertNotNull(ctx.getBean(ChaosService.class));
                    assertNotNull(ctx.getBean(ChaosFilter.class));
                    assertNotNull(ctx.getBean(ChaosController.class));
                });
    }

    @Test
    @DisplayName("enabled=false（默认）：不注册任何 R15 Bean，与既有行为零回归")
    void noBeansWhenDisabled() {
        runner.run(ctx -> {
            assertFalse(ctx.containsBean("chaosRegistry"));
            assertFalse(ctx.containsBean("chaosFilter"));
            assertFalse(ctx.containsBean("chaosController"));
        });
    }
}
