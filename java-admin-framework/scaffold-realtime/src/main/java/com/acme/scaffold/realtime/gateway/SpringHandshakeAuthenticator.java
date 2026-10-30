package com.acme.scaffold.realtime.gateway;

import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;

/**
 * 复用框架既有 JWT 解析端口的握手鉴权适配。
 *
 * <p>把 {@link HandshakeAuthenticator}（wg 端口）桥接到 realtime 模块的
 * {@link HandshakeTokenAuthenticator}（SPI，JWT 解析由认证模块以适配器注入），
 * 使 {@link GatewayNode#onHandshake} 的鉴权逻辑复用框架既有安全实现，
 * 保持 realtime → security 的单向依赖、不反向耦合。
 */
public final class SpringHandshakeAuthenticator implements HandshakeAuthenticator {

    private final HandshakeTokenAuthenticator resolver;

    public SpringHandshakeAuthenticator(HandshakeTokenAuthenticator resolver) {
        this.resolver = resolver;
    }

    @Override
    public AuthResult authenticate(String token, long now) {
        Long userId = resolver.resolveUserId(token);
        if (userId == null) {
            return AuthResult.reject("令牌无效或已过期");
        }
        return AuthResult.ok(String.valueOf(userId));
    }
}
