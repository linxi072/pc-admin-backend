package com.acme.scaffold.chaos;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 混沌工程装配（R15）。
 *
 * <p>默认 {@code app.chaos.enabled=false} 时整体不注册任何 Bean（注册表 / 过滤器 / 控制器），
 * 与既有请求链路零回归。启用时注册内存故障注册表、请求链路故障注入过滤器与运维控制器，
 * 用于韧性/混沌演练；演练结束务必关闭开关或调用 clear 清除规则。</p>
 */
@Configuration
@EnableConfigurationProperties(ChaosProperties.class)
@ConditionalOnProperty(prefix = "app.chaos", name = "enabled", havingValue = "true")
public class ChaosAutoConfiguration {

    @Bean
    public ChaosRegistry chaosRegistry() {
        return new ChaosRegistry();
    }

    @Bean
    public ChaosService chaosService(ChaosRegistry registry) {
        return new ChaosService(registry);
    }

    @Bean
    public ChaosFilter chaosFilter(ChaosRegistry registry, ChaosProperties properties) {
        return new ChaosFilter(registry, properties);
    }
}
