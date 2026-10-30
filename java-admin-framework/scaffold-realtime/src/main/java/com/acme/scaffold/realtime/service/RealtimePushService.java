package com.acme.scaffold.realtime.service;

import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.MsgType;
import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.acme.scaffold.realtime.payload.RealtimeMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 实时推送服务：将业务事件（未读消息、待办提醒、系统通知）按 userId 定向推送到在线 WebSocket 会话。
 *
 * <p>整合说明：原实现经 {@code RealtimeSessionManager} 直接 {@code TextMessage} 下发；本次整合后
 * 下行统一走 {@link GatewayNode#deliver}/{@link GatewayNode#broadcast}，承载于已迁移的
 * {@code common.messaging} 二进制信封（{@link Envelope}）——保持「先序列化 → 失败即放弃」的既有语义，
 * 仅把传输从文本帧切换为 wg 二进制帧。业务负载仍是 {@link RealtimeMessage} JSON，前端渲染契约不变。
 *
 * <p>{@code GatewayNode} 在 {@code realtime.gateway.enabled=false} 时不装配，
 * 此时本服务降级为「全部返回 0」（与离线用户语义一致，前端进入页面时通过 REST 拉取全量）。
 *
 * <p>单用户定向推送遵循 wg「一用户一本节点一条活跃连接」不变量：每次 deliver 命中最新的那条连接。
 */
@Service
public class RealtimePushService {

    private static final Logger log = LoggerFactory.getLogger(RealtimePushService.class);

    public static final String TYPE_UNREAD_MESSAGE = "UNREAD_MESSAGE";
    public static final String TYPE_TODO_REMINDER = "TODO_REMINDER";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_PONG = "PONG";

    /** 业务推送类别：实时类（可丢最旧），与 wg 慢消费者 L1 处置一致，避免断链。 */
    private static final boolean PUSH_IS_REALTIME = true;

    private final ObjectProvider<GatewayNode> gatewayNodeProvider;
    private final ObjectMapper objectMapper;

    public RealtimePushService(ObjectProvider<GatewayNode> gatewayNodeProvider, ObjectMapper objectMapper) {
        this.gatewayNodeProvider = gatewayNodeProvider;
        this.objectMapper = objectMapper;
    }

    /** 向单个用户推送，返回成功触达的活跃连接数（0 表示用户离线 / 序列化失败 / 网关未启用）。 */
    public int sendToUser(Long userId, String type, Object payload) {
        byte[] bytes = toJsonBytes(type, payload);
        if (bytes == null) {
            return 0;
        }
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return 0;
        }
        Envelope envelope = buildEnvelope(String.valueOf(userId), bytes);
        GatewayNode.DeliverOutcome outcome = node.deliver(String.valueOf(userId), envelope, PUSH_IS_REALTIME,
                System.currentTimeMillis());
        return outcome.status() == GatewayNode.DeliverStatus.DELIVERED ? 1 : 0;
    }

    /** 向多个用户批量推送，返回成功触达的用户数。 */
    public int sendToUsers(Collection<Long> userIds, String type, Object payload) {
        byte[] bytes = toJsonBytes(type, payload);
        if (bytes == null) {
            return 0;
        }
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return 0;
        }
        int total = 0;
        for (Long userId : userIds) {
            Envelope envelope = buildEnvelope(String.valueOf(userId), bytes);
            GatewayNode.DeliverOutcome outcome = node.deliver(String.valueOf(userId), envelope, PUSH_IS_REALTIME,
                    System.currentTimeMillis());
            if (outcome.status() == GatewayNode.DeliverStatus.DELIVERED) {
                total++;
            }
        }
        return total;
    }

    /** 向所有在线会话广播，返回成功触达的连接数。 */
    public int broadcast(String type, Object payload) {
        byte[] bytes = toJsonBytes(type, payload);
        if (bytes == null) {
            return 0;
        }
        GatewayNode node = gatewayNodeProvider.getIfAvailable();
        if (node == null) {
            return 0;
        }
        Envelope envelope = buildEnvelope("broadcast", bytes);
        List<GatewayNode.DeliverOutcome> outcomes = node.broadcast(envelope, PUSH_IS_REALTIME, System.currentTimeMillis());
        int delivered = 0;
        for (GatewayNode.DeliverOutcome outcome : outcomes) {
            if (outcome.status() == GatewayNode.DeliverStatus.DELIVERED) {
                delivered++;
            }
        }
        return delivered;
    }

    private Envelope buildEnvelope(String key, byte[] payloadBytes) {
        return new Envelope(Envelope.SEQ_UNASSIGNED,
                UUID.randomUUID().toString(),
                MsgType.DATA,
                key,
                payloadBytes,
                System.currentTimeMillis(),
                "");
    }

    /** 序列化失败返回 {@code null}（而非空串）：由调用方放弃发送，避免客户端收到空负载而计数虚高。 */
    private byte[] toJsonBytes(String type, Object payload) {
        try {
            return objectMapper.writeValueAsBytes(RealtimeMessage.of(type, payload));
        } catch (JsonProcessingException e) {
            log.error("实时消息序列化失败 type={}，本次推送放弃", type, e);
            return null;
        }
    }
}
