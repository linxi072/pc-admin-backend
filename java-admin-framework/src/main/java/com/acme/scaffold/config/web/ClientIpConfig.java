package com.acme.scaffold.config.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 客户端 IP 解析装配。
 *
 * <p>参考 {@code config.web} 下其它装配类（如 {@code WebConfig}）的写法：用
 * {@link EnableConfigurationProperties} 显式注册 {@link ClientIpProperties}，
 * 避免依赖全局 {@code @ConfigurationPropertiesScan} 扫描范围。</p>
 */
@Configuration
@EnableConfigurationProperties(ClientIpProperties.class)
public class ClientIpConfig {

    /**
     * 全局唯一的 IP 解析器：审计 / 限流 / 认证共用同一套可信代理口径，
     * 避免各处实现不一致导致「限流按 A 口径、审计按 B 口径」的溯源错位。
     */
    @Bean
    public ClientIpResolver clientIpResolver(ClientIpProperties properties) {
        return new ClientIpResolver(properties.getTrustedProxyCount());
    }
}
