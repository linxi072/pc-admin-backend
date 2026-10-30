package com.acme.scaffold.realtime.websocket;

import com.acme.scaffold.realtime.websocket.WebSocketOriginPolicy;
import org.springframework.stereotype.Component;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 握手拦截器：从 query {@code ?token=} 取令牌，经 {@link WebSocketOriginPolicy} 做 CSWSH 源站白名单校验，
 * 通过后把令牌透传给 {@link RealtimeWebSocketHandler}（存入会话属性 {@link #ATTR_TOKEN}）。
 *
 * <p>鉴权本身不在此处完成——握手令牌的解析与用户身份判定统一交给 {@code GatewayNode#onHandshake}
 * （经 {@code SpringHandshakeAuthenticator} 复用框架 JWT 解析端口），避免与处理器重复鉴权、
 * 并让鉴权成为节点内核的可测试纯逻辑。本拦截器只负责「取令牌 + 源站防御 + 透传」。
 *
 * <p>令牌缺失或源站不在白名单时直接拒绝握手（返回 false），对应 SecurityConfig 已对 /ws/** 放行、
 * 鉴权与源站防御由此处负责。
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    /** 会话属性键：握手后写入的令牌原文，供 {@link RealtimeWebSocketHandler} 交给节点鉴权。 */
    public static final String ATTR_TOKEN = "token";

    private final WebSocketOriginPolicy originPolicy;

    public RealtimeHandshakeInterceptor(WebSocketOriginPolicy originPolicy) {
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
        attributes.put(ATTR_TOKEN, token);
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
