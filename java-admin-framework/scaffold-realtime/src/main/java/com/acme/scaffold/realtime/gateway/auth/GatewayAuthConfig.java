package com.acme.scaffold.realtime.gateway.auth;

import com.acme.scaffold.realtime.gateway.GatewayProperties;
import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * HMAC 短期令牌鉴权装配（{@code realtime.gateway.hmac.enabled=true} 时激活）。
 *
 * <p>以 {@code @Primary} 覆盖默认的 JWT 解析路径，使 {@code GatewayNode} 的握手鉴权走
 * wg 的 HMAC 短期令牌形态。本装配独立于 {@code GatewayNodeConfig}，
 * 保持 {@code gateway → gateway.auth} 无依赖（仅 auth → gateway 的「实现依赖端口」方向），
 * 避免包级循环依赖。
 */
@Configuration
@ConditionalOnProperty(prefix = "realtime.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayAuthConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "realtime.gateway.hmac", name = "enabled", havingValue = "true")
    public HandshakeTokenAuthenticator hmacHandshakeAuthenticator(GatewayProperties props) {
        return new WgHandshakeTokenAuthenticator(props.getHmac().getSecret());
    }
}
