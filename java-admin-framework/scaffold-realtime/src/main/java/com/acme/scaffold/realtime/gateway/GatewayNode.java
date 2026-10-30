package com.acme.scaffold.realtime.gateway;

import com.acme.scaffold.common.messaging.bus.BusMetrics;
import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.codec.ControlCodec;
import com.acme.scaffold.common.messaging.codec.CorruptFrameException;
import com.acme.scaffold.common.messaging.codec.FrameCodec;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.MsgType;
import com.acme.scaffold.common.messaging.domain.Tier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关节点编排器（对应设计稿 L2 WebSocket 网关的全部核心行为）。
 *
 * <p>职责边界：本类是「有连接状态、无业务状态」的节点内核（§2.2）——
 * 握手鉴权、上行限流与双写生产、下行寻路与慢消费者处置、心跳保活、
 * 优雅退出七步，全部在此编排；不感知 Netty/Spring WebSocket 具体实现
 * （分别经 {@link ConnChannel} 与 {@link MessageBus} 端口隔离）。
 *
 * <p>关键链路：
 * <pre>
 *   上行：握手 → onFrame（限流 → 分配 seq → RT+DURABLE 双写 → 计数）
 *   下行：deliver（registry 寻路 → 慢消费者分级 → 帧下发）
 *   保活：onTick（ping 发放 / 空闲断开 / 路由 TTL 清理 / 节点熔断）
 *   退出：drainTick（七步状态机推进，带超时兜底）
 * </pre>
 */
public final class GatewayNode {

    private final GatewaySettings settings;
    private final MessageBus bus;
    private final Clock clock;
    private final String gatewayId;

    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final BackpressureCounter backpressure = new BackpressureCounter();
    private final DrainState drain;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final HeartbeatPolicy heartbeat;

    private boolean producerFlushed;

    public GatewayNode(GatewaySettings settings, MessageBus bus, Clock clock, String gatewayId) {
        this.settings = settings;
        this.bus = bus;
        this.clock = clock;
        this.gatewayId = gatewayId;
        this.heartbeat = settings.heartbeat();
        this.drain = new DrainState(settings.drainInflightTimeoutMs(), settings.drainFlushTimeoutMs());
    }

    public String gatewayId() {
        return gatewayId;
    }

    public ConnectionRegistry registry() {
        return registry;
    }

    public GatewaySettings settings() {
        return settings;
    }

    // ================================================================ 握手

    /** 握手状态。 */
    public enum HandshakeStatus {
        OK,
        /** 节点排空中，拒绝新连接（§3.6 第 2 步）。 */
        DRAINING,
        /** 节点容量耗尽（§3.7：20 万/节点）。 */
        CAPACITY_EXCEEDED,
        /** 鉴权失败（§3.2）。 */
        AUTH_REJECTED
    }

    public record HandshakeOutcome(HandshakeStatus status, Session session, int closeCode, String reason) {

        static HandshakeOutcome ok(Session session) {
            return new HandshakeOutcome(HandshakeStatus.OK, session, -1, null);
        }

        static HandshakeOutcome rejected(HandshakeStatus status, int closeCode, String reason) {
            return new HandshakeOutcome(status, null, closeCode, reason);
        }
    }

    /**
     * 握手处理：鉴权 → 容量 → 注册 → 建会话。
     *
     * <p>同用户重连时旧会话被顶替（关闭码 4009），旧通道由调用方关闭；
     * 这保证「一个用户本节点一条活跃连接」不变量（§3.4 epoch 语义）。
     */
    public HandshakeOutcome onHandshake(String token, HandshakeAuthenticator auth, ConnChannel channel, long now) {
        if (drain.isDraining()) {
            backpressure.onCircuitBreakRejected();
            return HandshakeOutcome.rejected(HandshakeStatus.DRAINING,
                    settings.closeCodeDraining(), "节点排空中，拒绝新握手");
        }
        if (registry.userCount() >= settings.maxConnectionsPerNode()) {
            backpressure.onCircuitBreakRejected();
            return HandshakeOutcome.rejected(HandshakeStatus.CAPACITY_EXCEEDED,
                    settings.closeCodeCapacity(), "节点连接数达上限: " + settings.maxConnectionsPerNode());
        }
        HandshakeAuthenticator.AuthResult authResult = auth.authenticate(token, now);
        if (!authResult.ok()) {
            backpressure.onAuthRejected();
            return HandshakeOutcome.rejected(HandshakeStatus.AUTH_REJECTED,
                    settings.closeCodeAuthFailed(), authResult.reason());
        }

        String userId = authResult.userId();
        String evicted = registry.register(channel.connId(), userId, gatewayId, now);
        if (evicted != null) {
            Session old = sessions.remove(evicted);
            if (old != null) {
                old.markClosed();
                old.channel().close(settings.closeCodeSessionReplaced(), "session replaced by reconnect");
            }
        }
        Session session = new Session(channel.connId(), userId, registry.findConn(userId)
                .map(RouteEntry::epoch).orElse(0L), now, channel,
                new RateLimiter(settings.ratePerConnPerSec(), settings.burstPerConn(), clock));
        sessions.put(channel.connId(), session);
        return HandshakeOutcome.ok(session);
    }

    // ================================================================ 上行

    /** 上行处理状态。 */
    public enum IngestStatus {
        OK,
        /** 会话已关闭/通道不活跃（迟到帧）。 */
        IGNORED_INACTIVE,
        /** 协议错误（帧损坏/未知类型），连接按 4003 关闭。 */
        PROTOCOL_REJECTED,
        /** 触发单连接限流（§3.2，4028）。 */
        RATE_LIMITED,
        /** 双写中至少一个通道拒绝（计入总线指标，不断链）。 */
        BUS_PARTIAL_REJECT
    }

    public record IngestOutcome(IngestStatus status, long seq, String msgId, String reason) {

        static IngestOutcome ok(long seq, String msgId) {
            return new IngestOutcome(IngestStatus.OK, seq, msgId, null);
        }

        static IngestOutcome of(IngestStatus status, String reason) {
            return new IngestOutcome(status, -1L, null, reason);
        }
    }

    /**
     * 上行帧处理：解码 → 限流 → 分配 seq → RT+DURABLE 双写（§2.3）。
     *
     * <p>双写非事务：任一路径 rejected 不影响另一路，客户端仍收到成功语义
     * （失败计入总线指标由对账通道兜底，§2.3 设计说明）。
     */
    public IngestOutcome onFrame(Session session, byte[] frame, long now) {
        if (session == null || session.isClosed() || !session.channel().isActive()) {
            return IngestOutcome.of(IngestStatus.IGNORED_INACTIVE, "会话已关闭");
        }
        markActivity(session, now);

        // 双魔数路由：控制帧（0x41，服务端 ping/pong/ack 的同形态）与数据帧（0x57）分流，
        // 避免客户端回传 ControlCodec 形态的 PONG/ACK 被当成损坏数据帧而误断链（4003）。
        if (frame == null || frame.length == 0) {
            return rejectProtocol(session, "空帧");
        }
        int magic = frame[0] & 0xFF;
        if (magic == ControlCodec.MAGIC) {
            return onControlFrame(session, frame);
        }

        final Envelope decoded;
        try {
            decoded = FrameCodec.decode(frame);
        } catch (CorruptFrameException ex) {
            return rejectProtocol(session, ex.getMessage());
        }

        switch (decoded.type()) {
            case ACK -> {
                session.markAcked(decoded.seq());
                return IngestOutcome.ok(decoded.seq(), decoded.msgId());
            }
            case PING -> {
                session.channel().writeFrame(ControlCodec.encodePong());
                return IngestOutcome.ok(session.peekNextSeq(), decoded.msgId());
            }
            case PONG, CLOSE, RESUME -> {
                if (decoded.type() == MsgType.CLOSE) {
                    dropSession(session);
                }
                return IngestOutcome.ok(session.peekNextSeq(), decoded.msgId());
            }
            case DATA -> {
                // 单连接令牌桶（§3.2：100/s、突发 500）
                if (!session.limiter().tryConsume()) {
                    backpressure.onRateLimited();
                    session.channel().close(settings.closeCodeRateLimited(), "触发单连接限流");
                    dropSession(session);
                    return IngestOutcome.of(IngestStatus.RATE_LIMITED,
                            "超过 " + settings.ratePerConnPerSec() + " msg/s（突发 " + settings.burstPerConn() + "）");
                }
                long seq = session.nextSeq();
                Envelope assigned = decoded.withSeq(seq);
                ProduceResult rt = bus.produce(assigned, Tier.RT);
                ProduceResult durable = bus.produce(assigned, Tier.DURABLE);
                if (!rt.accepted() || !durable.accepted()) {
                    return new IngestOutcome(IngestStatus.BUS_PARTIAL_REJECT, seq, assigned.msgId(),
                            "rt=" + rt.errorMessage() + ", durable=" + durable.errorMessage());
                }
                return IngestOutcome.ok(seq, assigned.msgId());
            }
            default -> {
                // 与 IngestStatus#PROTOCOL_REJECTED 的契约一致：未知类型按协议错误关连接（4003）
                return rejectProtocol(session, "未覆盖类型: " + decoded.type());
            }
        }
    }

    /**
     * 控制帧（{@link ControlCodec}，魔数 0x41）上行处理。
     *
     * <p>容忍客户端以服务端同形态回传 PONG/ACK/CLOSE（否则会被当成损坏数据帧误断链）；
     * 仅 PONG/ACK/CLOSE 有定义语义，其余控制类型按协议错误关闭（4003）。
     */
    private IngestOutcome onControlFrame(Session session, byte[] frame) {
        final ControlCodec.ControlFrame control;
        try {
            control = ControlCodec.decode(frame);
        } catch (CorruptFrameException ex) {
            return rejectProtocol(session, ex.getMessage());
        }
        switch (control.type()) {
            case PONG -> {
                return IngestOutcome.ok(session.peekNextSeq(), "pong");
            }
            case ACK -> {
                session.markAcked(control.seq());
                return IngestOutcome.ok(control.seq(), "ack");
            }
            case CLOSE -> {
                dropSession(session);
                return IngestOutcome.ok(session.peekNextSeq(), "close");
            }
            default -> {
                return rejectProtocol(session, "未覆盖控制帧类型: " + control.type());
            }
        }
    }

    /** 协议错误统一处置：计数 → 4003 关闭 → 清理会话与路由。 */
    private IngestOutcome rejectProtocol(Session session, String reason) {
        backpressure.onProtocolRejected();
        session.channel().close(settings.closeCodeProtocol(), "协议错误: " + reason);
        dropSession(session);
        return IngestOutcome.of(IngestStatus.PROTOCOL_REJECTED, reason);
    }

    /** 会话摘除：标记关闭 + 同步清理会话表与路由表（幂等）。 */
    private void dropSession(Session session) {
        session.markClosed();
        sessions.remove(session.connId());
        registry.remove(session.connId());
    }

    /** 客户端 ACK：推进已确认水位（§3.3）。 */
    public void onAck(Session session, long seq) {
        if (session != null && !session.isClosed()) {
            session.markAcked(seq);
        }
    }

    /** 客户端 PONG：刷新读活动时间。 */
    public void onPong(Session session, long now) {
        if (session != null && !session.isClosed()) {
            markActivity(session, now);
        }
    }

    /**
     * 活动刷新：会话读时间 + 路由表 lastSeen 同步推进。
     *
     * <p>二者必须同时刷新：路由 TTL（90s）以 lastSeen 判定存活，漏刷会把长连接
     * （浏览器页面常驻）误判为幽灵路由而摘除，导致推送静默失效（§3.4）。
     */
    private void markActivity(Session session, long now) {
        session.markRead(now);
        registry.touch(session.connId(), now);
    }

    // ================================================================ 下行

    /** 下行投递状态。 */
    public enum DeliverStatus {
        DELIVERED,
        /** L1：实时类消息，丢弃最旧（§3.5）。 */
        DROPPED_OLDEST,
        /** L2：关键类消息，断链补洞（§3.5）。 */
        CLOSED_SLOW_CONSUMER,
        /** 用户不在本节点（调用方走跨网关扇出）。 */
        NOT_LOCAL,
        /** 会话已关闭。 */
        SESSION_CLOSED
    }

    public record DeliverOutcome(DeliverStatus status, long seq, String connId) {

        static DeliverOutcome of(DeliverStatus status, long seq, String connId) {
            return new DeliverOutcome(status, seq, connId);
        }
    }

    /**
     * 定向推送（§3.4）：registry 寻路 → 慢消费者分级 → 分配 seq → 下发。
     *
     * @param realtime 消息类别：true=实时类（遥测/行情，可丢最旧）；false=关键类（断链补洞）
     */
    public DeliverOutcome deliver(String userId, Envelope envelope, boolean realtime, long now) {
        Optional<RouteEntry> found = registry.findConn(userId);
        if (found.isEmpty()) {
            return DeliverOutcome.of(DeliverStatus.NOT_LOCAL, -1L, null);
        }
        Session session = sessions.get(found.get().connId());
        if (session == null || session.isClosed() || !session.channel().isActive()) {
            return DeliverOutcome.of(DeliverStatus.SESSION_CLOSED, -1L, found.get().connId());
        }
        markActivity(session, now);

        SlowConsumerPolicy.SlowLevel level = settings.slowConsumer()
                .classify(session.channel().inflight(), realtime);
        if (level == SlowConsumerPolicy.SlowLevel.L2_CLOSE_4029) {
            backpressure.onSlowClosed();
            session.channel().close(settings.closeCodeSlowConsumer(), "慢消费者断链，请重连续传");
            dropSession(session);
            return DeliverOutcome.of(DeliverStatus.CLOSED_SLOW_CONSUMER, -1L, session.connId());
        }
        if (level == SlowConsumerPolicy.SlowLevel.L1_DROP_OLDEST) {
            // 丢弃最旧由通道实现执行（队列溢出丢头部），这里记账（§3.5）
            session.channel().markDroppedOldest();
            backpressure.onDropOldest();
            return DeliverOutcome.of(DeliverStatus.DROPPED_OLDEST, -1L, session.connId());
        }

        long seq = session.nextSeq();
        session.channel().writeFrame(FrameCodec.encode(envelope.withSeq(seq)));
        return DeliverOutcome.of(DeliverStatus.DELIVERED, seq, session.connId());
    }

    /**
     * 广播（运维/系统公告）：遍历本节点所有在线连接逐户下发。
     *
     * @return 各连接的投递结果
     */
    public List<DeliverOutcome> broadcast(Envelope envelope, boolean realtime, long now) {
        List<DeliverOutcome> outcomes = new ArrayList<>();
        for (RouteEntry entry : registry.snapshot()) {
            outcomes.add(deliver(entry.userId(), envelope, realtime, now));
        }
        return outcomes;
    }

    // ================================================================ 保活

    /** 一次心跳 tick 的结果（供观测与测试断言）。 */
    public record HeartbeatTickResult(List<String> pingsSent, List<String> idleClosed,
                                      List<String> routesExpired, boolean nodeCircuitBroken,
                                      int totalInflight) {
    }

    /**
     * 心跳 tick（§3.3）：发 ping / 断空闲 / 清幽灵路由 / 节点级熔断判定。
     *
     * <p>生产中由定时器周期调用（30s）；测试用手动时钟驱动。
     */
    public HeartbeatTickResult onTick(long now) {
        List<String> pings = new ArrayList<>();
        List<String> idleClosed = new ArrayList<>();
        for (Session session : sessions.values()) {
            if (session.isClosed() || !session.channel().isActive()) {
                // 无 close 回调到达时的兜底回收：失效会话不得滞留会话表（否则内存泄漏，
                // 且 onlineUserCounts/totalInflight 持续计入幽灵连接）
                dropSession(session);
                continue;
            }
            if (heartbeat.isIdleExpired(session.lastReadAt(), now)) {
                backpressure.onIdleClosed();
                session.channel().close(settings.closeCodeIdleTimeout(), "读写空闲超时");
                dropSession(session);
                idleClosed.add(session.connId());
            } else if (heartbeat.isPingDue(session.lastPingAt(), now)) {
                session.channel().writeFrame(ControlCodec.encodePing());
                session.markPinged(now);
                // 成功写 ping 即证明通道存活：同步刷新路由 lastSeen，防长连接被 TTL 误摘
                registry.touch(session.connId(), now);
                pings.add(session.connId());
            }
        }
        List<String> expired = registry.expireIdle(heartbeat.routeTtlMs(), now);
        for (String connId : expired) {
            // 路由 TTL 过期（幽灵连接兜底）必须同步清理会话表，否则 Session 对象成为孤儿
            Session orphan = sessions.remove(connId);
            if (orphan != null) {
                orphan.markClosed();
            }
        }

        int inflightTotal = totalInflight();
        double nodeCapacity = (double) settings.maxConnectionsPerNode() * settings.slowConsumer().queueLimit();
        double ratio = nodeCapacity > 0 ? inflightTotal / nodeCapacity : 0.0;
        boolean circuitBroken = settings.slowConsumer().shouldCircuitBreak(ratio);
        return new HeartbeatTickResult(pings, idleClosed, expired, circuitBroken, inflightTotal);
    }

    /** 节点 in-flight 总量（发送队列水位之和，排空/熔断判定输入）。 */
    public int totalInflight() {
        int total = 0;
        for (Session session : sessions.values()) {
            if (!session.isClosed()) {
                total += session.channel().inflight();
            }
        }
        return total;
    }

    // ================================================================ 断开与退出

    /** 通道断开回调（§3.4 删除方：异常断开即清路由）。 */
    public void onChannelInactive(Session session) {
        if (session == null) {
            return;
        }
        session.markClosed();
        sessions.remove(session.connId());
        registry.remove(session.connId());
    }

    public boolean isDraining() {
        return drain.isDraining();
    }

    public DrainState.DrainStep drainStep() {
        return drain.step();
    }

    /**
     * 优雅退出推进（§3.6 七步）。每次调用按当前状态前进一步，
     * 带副作用：进入 CLIENTS_CLOSING 时全量 1001 关闭；进入 PRODUCER_FLUSHING
     * 时同步 flush 总线（5s 超时兜底）；TERMINATED 时清空路由表。
     *
     * @return 推进后的步骤
     */
    public DrainState.DrainStep drainTick(long now) {
        long elapsed = now - drain.stepStartedAt();
        DrainState.DrainStep next = drain.advance(
                DrainState.Signal.of(totalInflight(), connectionCount(), producerFlushed, elapsed), now);
        if (next == DrainState.DrainStep.CLIENTS_CLOSING) {
            closeAllClients();
        } else if (next == DrainState.DrainStep.PRODUCER_FLUSHING) {
            bus.flush(settings.drainFlushTimeoutMs());
            producerFlushed = true;
        } else if (next == DrainState.DrainStep.TERMINATED) {
            registry.clear();
            sessions.clear();
        }
        return next;
    }

    private void closeAllClients() {
        for (Session session : sessions.values()) {
            if (!session.isClosed()) {
                session.channel().close(settings.closeCodeDraining(), "draining, reconnect immediately");
            }
            session.markClosed();
            // 1001 已发出即视为本节点对该连接的处置完成：同步清理会话与路由
            // （步骤 7 的路由表删除在此提前兑现，防状态机卡在 CLIENTS_CLOSING）
            sessions.remove(session.connId());
            registry.remove(session.connId());
        }
    }

    // ================================================================ 查询

    public int connectionCount() {
        return registry.size();
    }

    public Optional<Session> sessionOf(String userId) {
        return registry.findConn(userId).map(entry -> sessions.get(entry.connId()));
    }

    /** 在线用户 → 活跃会话数的只读映射（多端累加），供管理端展示。 */
    public Map<Long, Integer> onlineUserCounts() {
        Map<Long, Integer> result = new HashMap<>();
        for (RouteEntry entry : registry.snapshot()) {
            Long uid;
            try {
                uid = Long.valueOf(entry.userId());
            } catch (NumberFormatException ex) {
                continue;
            }
            result.merge(uid, 1, Integer::sum);
        }
        return result;
    }

    public BackpressureCounter.Snapshot backpressure() {
        return backpressure.snapshot();
    }

    public BusMetrics busMetrics() {
        return bus.metrics();
    }
}
