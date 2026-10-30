package com.acme.scaffold.realtime.gateway.auth;

import com.acme.scaffold.realtime.gateway.HandshakeAuthenticator;
import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;

/**
 * 将 wg 的 HMAC 短期令牌鉴权桥接为 realtime 模块的 {@link HandshakeTokenAuthenticator} SPI。
 *
 * <p>当 {@code realtime.gateway.hmac.enabled=true} 时由 {@code GatewayNodeConfig} 注册为
 * {@code @Primary} Bean，替代默认的 JWT 解析路径；其余情况不激活，框架沿用既有 JWT 鉴权。
 */
public final class WgHandshakeTokenAuthenticator implements HandshakeTokenAuthenticator {

    private final HmacTokenAuthenticator delegate;

    public WgHandshakeTokenAuthenticator(String secret) {
        this.delegate = new HmacTokenAuthenticator(secret);
    }

    @Override
    public Long resolveUserId(String token) {
        HandshakeAuthenticator.AuthResult result = delegate.authenticate(token, System.currentTimeMillis());
        if (!result.ok() || result.userId() == null) {
            return null;
        }
        try {
            return Long.valueOf(result.userId());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
