package com.acme.scaffold.realtime.websocket;

import com.acme.scaffold.realtime.session.RealtimeSessionManager;
import com.acme.scaffold.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截器：从 query {@code ?token=} 解析 JWT，提取 userId 写入会话属性，供后续定向推送。
 * <p>校验失败返回 false 直接拒绝握手（对应 SecurityConfig 已对 /ws/** 放行，鉴权由此处负责）。
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtProvider jwtProvider;
    private final WebSocketOriginPolicy originPolicy;

    public RealtimeHandshakeInterceptor(JwtProvider jwtProvider, WebSocketOriginPolicy originPolicy) {
        this.jwtProvider = jwtProvider;
        this.originPolicy = originPolicy;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token == null) {
            return false;
        }
        // 纵深防御：Origin 头白名单校验（CSWSH 防护），失败直接拒绝握手
        if (!originPolicy.allows(request.getHeaders().getOrigin())) {
            return false;
        }
        try {
            Claims claims = jwtProvider.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            attributes.put(RealtimeSessionManager.ATTR_USER_ID, userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
