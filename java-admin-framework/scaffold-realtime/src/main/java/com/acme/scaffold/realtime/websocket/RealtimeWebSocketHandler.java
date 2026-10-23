package com.acme.scaffold.realtime.websocket;

import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.acme.scaffold.realtime.gateway.HandshakeAuthenticator;
import com.acme.scaffold.realtime.gateway.HandshakeAuthenticator.AuthResult;
import com.acme.scaffold.realtime.gateway.Session;
import com.acme.scaffold.realtime.gateway.SpringConnChannel;
import com.acme.scaffold.realtime.gateway.SpringHandshakeAuthenticator;
import com.acme.scaffold.realtime.port.HandshakeTokenAuthenticator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;

/**
 * 实时 WebSocket 处理器：将连接生命周期接入 {@link GatewayNode}。
 *
 * <p>连接建立 → 经 {@link GatewayNode#onHandshake} 鉴权（复用框架 JWT 解析端口）+ 注册；
 * 二进制帧 → {@link GatewayNode#onFrame}（解码/限流/分配 seq/RT+DURABLE 双写/ACK-PING-PONG）；
 * 关闭 → {@link GatewayNode#onChannelInactive} 清路由。鉴权令牌由
 * {@link RealtimeHandshakeInterceptor} 透传，CSWSH 的 Origin 防御亦在其完成。
 *
 * <p>节点未装配（{@code realtime.gateway.enabled=false}）时降级为直接拒绝连接。
 */
@Component
public class RealtimeWebSocketHandler extends BinaryWebSocketHandler {

    /** 会话属性键：握手后写入的 wg 会话对象。 */
    public static final String ATTR_WG_SESSION = "wgSession";

    private final ObjectProvider<GatewayNode> gatewayNodeProvider;
    private final HandshakeTokenAuthenticator tokenAuthenticator;

    public RealtimeWebSocketHandler(ObjectProvider<GatewayNode> gatewayNodeProvider,
                                   HandshakeTokenAuthenticator tokenAuthenticator) {
        this.gatewayNodeProvider = gatewayNodeProvider;
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            session.close(new CloseStatus(4500, "realtime gateway disabled"));
            return;
        }
        String token = (String) session.getAttributes().get(RealtimeHandshakeInterceptor.ATTR_TOKEN);
        SpringConnChannel channel = new SpringConnChannel(session);
        HandshakeAuthenticator auth = new SpringHandshakeAuthenticator(tokenAuthenticator);
        GatewayNode.HandshakeOutcome outcome = node.onHandshake(
                token, auth, channel, System.currentTimeMillis());
        if (outcome.status() != GatewayNode.HandshakeStatus.OK) {
            session.close(new CloseStatus(outcome.closeCode(), outcome.reason()));
            return;
        }
        session.getAttributes().put(ATTR_WG_SESSION, outcome.session());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return;
        }
        Session wg = (Session) session.getAttributes().get(ATTR_WG_SESSION);
        if (wg == null) {
            return;
        }
        ByteBuffer buf = message.getPayload();
        byte[] frame = new byte[buf.remaining()];
        buf.get(frame);
        node.onFrame(wg, frame, System.currentTimeMillis());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return;
        }
        Session wg = (Session) session.getAttributes().get(ATTR_WG_SESSION);
        if (wg != null) {
            node.onChannelInactive(wg);
        }
    }
}
