package com.acme.scaffold.realtime.websocket;

import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;
import com.acme.scaffold.realtime.session.RealtimeSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截器：从 query {@code ?token=} 取令牌，经 {@link HandshakeTokenAuthenticator} 端口
 * 解析出 userId 写入会话属性，供后续定向推送。
 * <p>校验失败返回 false 直接拒绝握手（对应 SecurityConfig 已对 /ws/** 放行，鉴权由此处负责）。
 * 本模块不直接依赖 JWT 实现——端口由认证模块（security.jwt）注入，保持 realtime 不反向依赖 system。
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    private final HandshakeTokenAuthenticator tokenAuthenticator;
    private final WebSocketOriginPolicy originPolicy;

    public RealtimeHandshakeInterceptor(HandshakeTokenAuthenticator tokenAuthenticator,
                                        WebSocketOriginPolicy originPolicy) {
        this.tokenAuthenticator = tokenAuthenticator;
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
        Long userId = tokenAuthenticator.resolveUserId(token);
        if (userId == null) {
            return false;
        }
        attributes.put(RealtimeSessionManager.ATTR_USER_ID, userId);
        return true;
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
