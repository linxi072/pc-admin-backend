package com.acme.scaffold.config;

import com.acme.scaffold.realtime.RealtimeHandshakeInterceptor;
import com.acme.scaffold.realtime.RealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 原生 WebSocket 端点配置：暴露 {@code /ws}（握手需带 {@code ?token=<JWT>}）。
 * <p>鉴权由 {@link RealtimeHandshakeInterceptor} 完成；跨域放开便于本地前后端分离联调，
 * 生产应改为具体前端域名。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler handler;
    private final RealtimeHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(RealtimeWebSocketHandler handler,
                           RealtimeHandshakeInterceptor handshakeInterceptor) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
