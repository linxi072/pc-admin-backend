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

    // ---------------------------------------------------------------- 协议容错（双魔数）

    @Test
    @DisplayName("控制帧 PONG（0x41）上行：容忍不断链（旧行为会误判损坏帧 4003）")
    void onFrame_controlPong_tolerated() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();

        GatewayNode.IngestOutcome out = node.onFrame(hs.session(), ControlCodec.encodePong(), CLOCK.nowMillis());

        assertEquals(GatewayNode.IngestStatus.OK, out.status());
        assertEquals(-1, ch.closeCode(), "不得关闭连接");
        assertEquals(1, node.connectionCount());
    }

    @Test
    @DisplayName("控制帧 ACK（0x41）上行：推进已确认水位")
    void onFrame_controlAck_advancesWatermark() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");

        node.onFrame(hs.session(), ControlCodec.encodeAck(9L), CLOCK.nowMillis());

        assertEquals(9L, hs.session().lastAckedSeq());
    }

    @Test
    @DisplayName("控制帧 CLOSE（0x41）上行：清理会话与路由")
    void onFrame_controlClose_cleans() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");

        byte[] closeFrame = new byte[]{(byte) ControlCodec.MAGIC, 0x01, (byte) MsgType.CLOSE.code(), 0};
        GatewayNode.IngestOutcome out = node.onFrame(hs.session(), closeFrame, CLOCK.nowMillis());

        assertEquals(GatewayNode.IngestStatus.OK, out.status());
        assertEquals(0, node.connectionCount());
    }

    @Test
    @DisplayName("数据帧魔数损坏：协议错误关闭 4003 并清理")
    void onFrame_corruptMagic_closes4003() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();

        GatewayNode.IngestOutcome out = node.onFrame(hs.session(), new byte[]{0x00, 0x01, 0x02}, CLOCK.nowMillis());

        assertEquals(GatewayNode.IngestStatus.PROTOCOL_REJECTED, out.status());
        assertEquals(4003, ch.closeCode());
        assertEquals(0, node.connectionCount());
        assertTrue(node.backpressure().protocolRejected() >= 1);
    }

    // ---------------------------------------------------------------- 会话/路由回收

    @Test
    @DisplayName("心跳 tick：无 close 回调的失效会话被兜底回收（防内存泄漏）")
    void onTick_inactiveSession_reclaimed() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();
        ch.close(4009, "replaced"); // 模拟通道已失效但无 afterConnectionClosed 回调

        node.onTick(CLOCK.nowMillis());

        assertEquals(0, node.connectionCount(), "失效会话必须被 tick 回收");
    }

    @Test
    @DisplayName("长连接保活：ping 刷新路由 lastSeen，超过 TTL 不被误摘（回归：touch 漏刷）")
    void longLivedConnection_routeNotExpired() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();

        // 模拟 10 分钟长连接：每 30s 客户端回 PONG（刷新读活动）+ 服务端 tick 发 ping（刷路由）
        for (int i = 0; i < 20; i++) {
            CLOCK.advanceBy(30_000L);
            node.onFrame(hs.session(), ControlCodec.encodePong(), CLOCK.nowMillis());
            node.onTick(CLOCK.nowMillis());
        }

        assertEquals(1, node.connectionCount(), "活跃长连接的路由不得被 TTL 误摘");
        Envelope push = new Envelope(Envelope.SEQ_UNASSIGNED, "m9", MsgType.DATA, "1",
                "p".getBytes(), CLOCK.nowMillis(), "");
        GatewayNode.DeliverOutcome out = node.deliver("1", push, true, CLOCK.nowMillis());
        assertEquals(GatewayNode.DeliverStatus.DELIVERED, out.status(), "长连接推送必须仍可达");
        assertTrue(ch.writtenFrames().size() >= 20, "20 次 tick 至少 20 个 ping");
    }

    // ---------------------------------------------------------------- 优雅退出最坏路径

    @Test
    @DisplayName("优雅退出最坏路径：in-flight 30s 超时才排空，drain 必须到达 TERMINATED")
    void drain_worstCase_reachesTerminated() {
        GatewayNode node = newNode();
        GatewayNode.HandshakeOutcome hs = handshake(node, "c1", "1");
        FakeConnChannel ch = (FakeConnChannel) hs.session().channel();
        ch.setInflight(500); // 水位不归零：强制走 30s 超时兜底路径

        long now = CLOCK.nowMillis();
        DrainState.DrainStep step = node.drainStep();
        // 与 GatewayNodeConfig 关停生命周期同构：模拟时间截止驱动（最坏 3+30+2 步）
        long deadline = now + node.settings().drainInflightTimeoutMs()
                + node.settings().drainFlushTimeoutMs() + 10_000L;
        int ticks = 0;
        while (step != DrainState.DrainStep.TERMINATED && now < deadline) {
            now += 1000;
            step = node.drainTick(now);
            ticks++;
        }

        assertEquals(DrainState.DrainStep.TERMINATED, step, "截止时间内必须完成七步排空");
        assertTrue(ticks > 24, "最坏路径步数应超过旧的 24 次上限（本用例 " + ticks + " 步）");
        assertEquals(0, node.connectionCount(), "TERMINATED 后路由表已清空");
    }
}
