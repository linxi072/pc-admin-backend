package com.acme.scaffold.realtime.gateway;

import com.acme.scaffold.common.messaging.codec.ControlCodec;
import com.acme.scaffold.common.messaging.codec.FrameCodec;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.MsgType;
import com.acme.scaffold.common.messaging.domain.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GatewayNode} 核心编排器单测（含假通道 / 假总线，无网络、无 Kafka）。
 *
 * <p>覆盖：握手鉴权 / 容量 / 上行双写 / 下行寻路与慢消费者 / 广播 / 断连清理 / 在线视图。
 */
@DisplayName("GatewayNode 编排器")
class GatewayNodeTest {

    private static final MutableClock CLOCK = new MutableClock(1_000_000L);

    private GatewayNode newNode() {
        return new GatewayNode(GatewaySettings.designDefault(), new FakeBus(), CLOCK, "gw-1");
    }

    private static HandshakeAuthenticator okAuth(String userId) {
        return (token, now) -> HandshakeAuthenticator.AuthResult.ok(userId);
    }

    private GatewayNode.HandshakeOutcome handshake(GatewayNode node, String connId, String userId) {
        return node.onHandshake("tok", okAuth(userId), new FakeConnChannel(connId, userId), CLOCK.nowMillis());
    }

    private static byte[] dataFrame(long seq, String key, byte[] payload) {
        return FrameCodec.encode(new Envelope(seq, "m-" + seq, MsgType.DATA, key, payload, CLOCK.nowMillis(), ""));
    }

    @Test
    @DisplayName("握手成功：鉴权通过并注册，连接计数 +1")
    void handshake_ok_registers() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome outcome = handshake(node, "c1", "1");

        assertEquals(GatewayNode.HandshakeStatus.OK, outcome.status());
        assertNotNull(outcome.session());
        assertEquals(1, node.connectionCount());
        assertTrue(node.onlineUserCounts().containsKey(1L));
    }

    @Test
    @DisplayName("握手鉴权失败：返回 AUTH_REJECTED 与关闭码 4001")
    void handshake_authRejected() {
        GatewayNode node = newNode();
        HandshakeAuthenticator reject = (token, now) -> HandshakeAuthenticator.AuthResult.reject("bad");
        GatewayNode.HandshakeOutcome outcome = node.onHandshake("x", reject,
                new FakeConnChannel("c2", "2"), CLOCK.nowMillis());

        assertEquals(GatewayNode.HandshakeStatus.AUTH_REJECTED, outcome.status());
        assertEquals(4001, outcome.closeCode());
        assertEquals(0, node.connectionCount());
    }

    @Test
    @DisplayName("容量耗尽：单节点上限 1 时第二连接被拒（4027）")
    void handshake_capacityExceeded() {
        GatewaySettings cap1 = new GatewaySettings(1, GatewaySettings.designDefault().heartbeat(),
                100.0, 500.0, GatewaySettings.designDefault().slowConsumer(),
                30_000L, 5_000L, 4001, 4003, 4028, 4029, 4030, 1001, 4027, 4009);
        GatewayNode node = new GatewayNode(cap1, new FakeBus(), CLOCK, "gw-1");
        handshake(node, "c1", "1");
        GatewayNode.HandshakeOutcome second = handshake(node, "c2", "2");

        assertEquals(GatewayNode.HandshakeStatus.CAPACITY_EXCEEDED, second.status());
        assertEquals(4027, second.closeCode());
        assertEquals(1, node.connectionCount());
    }

    @Test
    @DisplayName("上行 DATA 帧：RT + DURABLE 双写至总线")
    void onFrame_data_doubleWrite() {
        FakeBus bus = new FakeBus();
        GatewayNode node = new GatewayNode(GatewaySettings.designDefault(), bus, CLOCK, "gw-1");
        GatewayNode.HandshakeOutcome hs = node.onHandshake("tok", okAuth("1"),
                new FakeConnChannel("c1", "1"), CLOCK.nowMillis());

        node.onFrame(hs.session(), dataFrame(Envelope.SEQ_UNASSIGNED, "k1", "hello".getBytes()), CLOCK.nowMillis());

        assertEquals(1, bus.count(Tier.RT));
        assertEquals(1, bus.count(Tier.DURABLE));
    }

    @Test
    @DisplayName("下行 deliver：命中用户连接并写入一帧，分配 seq")
    void deliver_toUser_writesFrame() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();

        Envelope push = new Envelope(Envelope.SEQ_UNASSIGNED, "m2", MsgType.DATA, "1",
                "payload".getBytes(), CLOCK.nowMillis(), "");
        GatewayNode.DeliverOutcome out = node.deliver("1", push, true, CLOCK.nowMillis());

        assertEquals(GatewayNode.DeliverStatus.DELIVERED, out.status());
        assertEquals(1, ch.writtenFrames().size());
    }

    @Test
    @DisplayName("下行慢消费者 L1：实时类消息在队列溢出时丢最旧，不写帧")
    void deliver_slowConsumer_L1_dropOldest() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();
        ch.setInflight(1001); // 超过 SlowConsumerPolicy 默认上限 1000

        Envelope push = new Envelope(Envelope.SEQ_UNASSIGNED, "m2", MsgType.DATA, "1",
                "p".getBytes(), CLOCK.nowMillis(), "");
        GatewayNode.DeliverOutcome out = node.deliver("1", push, true, CLOCK.nowMillis());

        assertEquals(GatewayNode.DeliverStatus.DROPPED_OLDEST, out.status());
        assertEquals(0, ch.writtenFrames().size());
        assertEquals(1, ch.droppedTotal());
    }

    @Test
    @DisplayName("下行慢消费者 L2：关键类消息在队列溢出时断链（4029）")
    void deliver_slowConsumer_L2_close() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();
        ch.setInflight(1001);

        Envelope push = new Envelope(Envelope.SEQ_UNASSIGNED, "m2", MsgType.DATA, "1",
                "p".getBytes(), CLOCK.nowMillis(), "");
        GatewayNode.DeliverOutcome out = node.deliver("1", push, false, CLOCK.nowMillis());

        assertEquals(GatewayNode.DeliverStatus.CLOSED_SLOW_CONSUMER, out.status());
        assertEquals(4029, ch.closeCode());
        assertFalse(ch.isActive());
    }

    @Test
    @DisplayName("广播：遍历本节点所有连接逐户下发")
    void broadcast_allConnections() {
        GatewayNode node = newNode();
        handshake(node, "c1", "1");
        handshake(node, "c2", "2");

        Envelope push = new Envelope(Envelope.SEQ_UNASSIGNED, "m3", MsgType.DATA, "broadcast",
                "x".getBytes(), CLOCK.nowMillis(), "");
        List<GatewayNode.DeliverOutcome> outcomes = node.broadcast(push, true, CLOCK.nowMillis());

        assertEquals(2, outcomes.size());
        assertTrue(outcomes.stream().allMatch(o -> o.status() == GatewayNode.DeliverStatus.DELIVERED));
    }

    @Test
    @DisplayName("PING 帧：回应 PONG 控制帧")
    void onFrame_ping_respondsPong() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();

        byte[] ping = FrameCodec.encode(new Envelope(Envelope.SEQ_UNASSIGNED, "m", MsgType.PING, "k",
                new byte[0], CLOCK.nowMillis(), ""));
        node.onFrame(hs.session(), ping, CLOCK.nowMillis());

        assertEquals(1, ch.writtenFrames().size());
        ControlCodec.ControlFrame cf = ControlCodec.decode(ch.writtenFrames().get(0));
        assertEquals(MsgType.PONG, cf.type());
    }

    @Test
    @DisplayName("ACK 帧：推进会话已确认水位")
    void onFrame_ack_advancesWatermark() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");

        byte[] ack = FrameCodec.encode(new Envelope(7L, "m", MsgType.ACK, "k",
                new byte[0], CLOCK.nowMillis(), ""));
        node.onFrame(hs.session(), ack, CLOCK.nowMillis());

        assertEquals(7L, hs.session().lastAckedSeq());
    }

    @Test
    @DisplayName("断连清理：onChannelInactive 移除会话与路由")
    void channelInactive_clears() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        assertEquals(1, node.connectionCount());

        node.onChannelInactive(hs.session());
        assertEquals(0, node.connectionCount());
        assertTrue(node.onlineUserCounts().isEmpty());
    }

    @Test
    @DisplayName("在线视图：userId -> 活跃连接数")
    void onlineUserCounts_mapping() {
        GatewayNode node = newNode();
        handshake(node, "c1", "1");
        handshake(node, "c2", "2");
        Map<Long, Integer> counts = node.onlineUserCounts();
        assertEquals(1, counts.get(1L));
        assertEquals(1, counts.get(2L));
    }
}
