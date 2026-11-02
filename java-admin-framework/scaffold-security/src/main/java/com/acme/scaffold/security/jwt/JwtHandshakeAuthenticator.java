package com.acme.scaffold.security.jwt;

import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 JWT 的 WebSocket 握手鉴权适配器：实现 realtime 模块的 {@link HandshakeTokenAuthenticator} 端口。
 *
 * <p>解析失败（签名不符 / 过期 /  kid 缺失等）统一返回 {@code null} 由握手方拒绝，
 * 不向客户端泄露失败原因（与 HTTP 链路的 401 语义保持克制一致）。
 */
@Slf4j
@Component
public class JwtHandshakeAuthenticator implements HandshakeTokenAuthenticator {

    private final JwtProvider jwtProvider;

    public JwtHandshakeAuthenticator(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(jwtProvider.parse(token).getSubject());
        } catch (Exception ex) {
            log.debug("WebSocket 握手令牌解析失败：{}", ex.getMessage());
            return null;
        }
    }
}
