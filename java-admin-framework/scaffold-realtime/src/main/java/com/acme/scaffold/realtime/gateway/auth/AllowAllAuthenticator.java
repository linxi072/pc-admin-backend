package com.acme.scaffold.realtime.gateway.auth;

import com.acme.scaffold.realtime.gateway.HandshakeAuthenticator;

/**
 * 演示/测试用放行鉴权：无令牌或任意令牌均放行。
 *
 * <p>⚠️ 仅限本地演示与探针联调；生产必须使用 {@link HmacTokenAuthenticator} 或等价方案。
 */
public final class AllowAllAuthenticator implements HandshakeAuthenticator {

    @Override
    public AuthResult authenticate(String token, long now) {
        if (token == null || token.isBlank()) {
            return AuthResult.ok("anonymous");
        }
        String userId = token.trim();
        return AuthResult.ok(userId.length() > 64 ? userId.substring(0, 64) : userId);
    }
}
