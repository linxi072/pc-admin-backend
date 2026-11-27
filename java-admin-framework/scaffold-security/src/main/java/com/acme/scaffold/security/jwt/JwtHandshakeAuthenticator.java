package com.acme.scaffold.security.jwt;

import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;
import com.acme.scaffold.security.token.service.TokenVersionService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 JWT 的 WebSocket 握手鉴权适配器：实现 realtime 模块的 {@link HandshakeTokenAuthenticator} 端口。
 *
 * <p>解析失败（签名不符 / 过期 / kid 缺失等）或 {@code tokenVersion} 已落后于库中版本
 * （强退 / 禁用后）统一返回 {@code null} 由握手方拒绝，不向客户端泄露失败原因
 * （与 HTTP 链路的 401 语义保持克制一致）。
 */
@Slf4j
@Component
public class JwtHandshakeAuthenticator implements HandshakeTokenAuthenticator {

    private final JwtProvider jwtProvider;
    private final TokenVersionService tokenVersionService;

    public JwtHandshakeAuthenticator(JwtProvider jwtProvider, TokenVersionService tokenVersionService) {
        this.jwtProvider = jwtProvider;
        this.tokenVersionService = tokenVersionService;
    }

    @Override
    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = jwtProvider.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            Object versionClaim = claims.get("tokenVersion");
            Integer claimVersion = versionClaim instanceof Number n ? n.intValue() : null;
            if (!tokenVersionService.isValid(userId, claimVersion)) {
                log.debug("WebSocket 握手令牌版本已失效 userId={}", userId);
                return null;
            }
            return userId;
        } catch (Exception ex) {
            log.debug("WebSocket 握手令牌解析失败：{}", ex.getMessage());
            return null;
        }
    }
}
