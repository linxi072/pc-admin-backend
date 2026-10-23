package com.acme.scaffold.realtime.service;

import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RealtimePushService} 单测（整合后版本）。
 *
 * <p>下行统一经 {@link GatewayNode#deliver}/{@link GatewayNode#broadcast}；本测试用 Mockito 桩接
 * {@code GatewayNode}（经 {@link ObjectProvider} 注入），验证：
 * <ul>
 *   <li>正常 payload：序列化后经节点投递，按 DeliverStatus 折算触达数；</li>
 *   <li>序列化失败：放弃发送且返回 0（不向下透传空负载、不调用节点）；</li>
 *   <li>网关未装配（{@code realtime.gateway.enabled=false}）：全部返回 0。</li>
 * </ul>
 */
class RealtimePushServiceTest {

    private final GatewayNode node = mock(GatewayNode.class);
    private final ObjectProvider<GatewayNode> provider = mock(ObjectProvider.class);
    private final RealtimePushService service =
            new RealtimePushService(provider, new ObjectMapper());

    @Test
    @DisplayName("正常 payload：序列化后经节点投递，按 DELIVERED 折算触达数")
    void happyPath_delegatesToGatewayNode() {
        when(provider.getIfAvailable()).thenReturn(node);
        when(node.deliver(eq("42"), any(Envelope.class), anyBoolean(), anyLong()))
                .thenReturn(new GatewayNode.DeliverOutcome(GatewayNode.DeliverStatus.DELIVERED, 1L, "c1"));

        int count = service.sendToUser(42L, RealtimePushService.TYPE_TODO_REMINDER,
                Map.of("title", "待办", "instanceId", "i-1"));

        assertEquals(1, count);
        verify(node).deliver(eq("42"), any(Envelope.class), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("广播：按 DELIVERED 结果计数返回触达连接数")
    void broadcast_countsDelivered() {
        when(provider.getIfAvailable()).thenReturn(node);
        when(node.broadcast(any(Envelope.class), anyBoolean(), anyLong()))
                .thenReturn(List.of(
                        new GatewayNode.DeliverOutcome(GatewayNode.DeliverStatus.DELIVERED, 1L, "c1"),
                        new GatewayNode.DeliverOutcome(GatewayNode.DeliverStatus.DELIVERED, 2L, "c2"),
                        new GatewayNode.DeliverOutcome(GatewayNode.DeliverStatus.NOT_LOCAL, -1L, null)));

        int count = service.broadcast(RealtimePushService.TYPE_NOTIFICATION, Map.of("title", "系统维护"));

        assertEquals(2, count);
    }

    @Test
    @DisplayName("序列化失败：放弃发送且返回 0（不调用节点）")
    void serializationFailure_skipsSendAndReturnsZero() {
        Object poison = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("getter 抛异常，Jackson 序列化必失败");
            }
        };

        assertEquals(0, service.sendToUser(42L, RealtimePushService.TYPE_NOTIFICATION, poison));
        assertEquals(0, service.sendToUsers(java.util.List.of(1L, 2L),
                RealtimePushService.TYPE_NOTIFICATION, poison));
        assertEquals(0, service.broadcast(RealtimePushService.TYPE_NOTIFICATION, poison));
        verifyNoInteractions(node);
    }

    @Test
    @DisplayName("网关未装配（realtime.gateway.enabled=false）：全部返回 0")
    void gatewayDisabled_returnsZero() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertEquals(0, service.sendToUser(42L, RealtimePushService.TYPE_NOTIFICATION, "hi"));
        assertEquals(0, service.broadcast(RealtimePushService.TYPE_NOTIFICATION, "hi"));
        verifyNoInteractions(node);
    }
}
