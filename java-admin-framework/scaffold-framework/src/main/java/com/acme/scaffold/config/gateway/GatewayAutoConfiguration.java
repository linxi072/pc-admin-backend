package com.acme.scaffold.config.gateway;

import com.acme.scaffold.common.web.ClientIpResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 轻量网关前置层自动装配（方向 B）。
 *
 * <p><b>默认关闭</b>：{@code gateway.enabled=false} 时本配置类整体不生效，
 * 不注册 {@link GatewayFilter}，单体直连行为零回归（对齐混沌/配置中心的 feature-gate 惯例，
 * 以及 PLATFORM-EVOLUTION-PLAN B1「双挂过渡」要求）。运维在 application(-prod).yml 显式置
 * {@code gateway.enabled: true} 即启用网关前置能力。</p>
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
@ConditionalOnProperty(prefix = "gateway", name = "enabled", havingValue = "true")
public class GatewayAutoConfiguration {

    /**
     * 网关前置过滤器：注入全局唯一的 {@link ClientIpResolver}，与审计/限流共用同一套可信代理口径，
     * 确保「网关限流键」与「后端审计 IP」一致，不因网关引入而错位。
     */
    @Bean
    public GatewayFilter gatewayFilter(GatewayProperties properties, ClientIpResolver clientIpResolver) {
        return new GatewayFilter(properties, clientIpResolver);
    }
}
