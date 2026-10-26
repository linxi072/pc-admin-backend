package com.acme.scaffold.realtime.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.dto.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.dto.RealtimePushResultView;
import com.acme.scaffold.realtime.dto.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.acme.scaffold.realtime.payload.RealtimeNotificationPayload;
import com.acme.scaffold.realtime.port.RealtimeTemplatePort;
import com.acme.scaffold.realtime.port.RealtimeTemplateRenderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RealtimeAdminService} 单测（M30 模板化）。
 *
 * <p>用 Mockito 桩接 {@link RealtimePushService} 与端口 {@link ObjectProvider}，
 * 验证模板路径与原始路径的解析分支：
 * <ul>
 *   <li>原始路径：未指定模板 → 按请求 type / payload 推送；</li>
 *   <li>模板路径：指定 templateCode → 经端口渲染为 NOTIFICATION + RealtimeNotificationPayload；</li>
 *   <li>端口缺失但指定模板 → 显式抛 CONFLICT，绝不静默降级；</li>
 *   <li>定向推送：模板路径同样渲染为 NOTIFICATION 负载。</li>
 * </ul>
 */
class RealtimeAdminServiceTest {

    private final ObjectProvider<GatewayNode> gatewayProvider = mock(ObjectProvider.class);
    private final RealtimePushService pushService = mock(RealtimePushService.class);
    private final ObjectProvider<RealtimeTemplatePort> templateProvider = mock(ObjectProvider.class);

    private final RealtimeAdminService adminService =
            new RealtimeAdminService(gatewayProvider, pushService, templateProvider);

    @Test
    @DisplayName("原始路径：未指定模板时按 type/payload 推送")
    void broadcastRawPath() {
        when(pushService.broadcast(eq("NOTIFICATION"), any())).thenReturn(3);
        RealtimeBroadcastRequest req = new RealtimeBroadcastRequest("NOTIFICATION", "hello", null, null);
        RealtimePushResultView result = adminService.broadcast(req);
        assertEquals(3, result.delivered());
        verify(pushService).broadcast("NOTIFICATION", "hello");
    }

    @Test
    @DisplayName("模板路径：指定 templateCode 时经端口渲染为 NOTIFICATION + RealtimeNotificationPayload")
    void broadcastTemplatePath() {
        RealtimeTemplatePort port = mock(RealtimeTemplatePort.class);
        when(templateProvider.getIfAvailable()).thenReturn(port);
        // 端口负责渲染：返回已是渲染后的标题/正文/消息类型（变量 ${x} 已被替换为 V）
        when(port.render(eq("TPL_WS"), eq(Map.of("x", "V")))).thenReturn(
                new RealtimeTemplateRenderResult("标题", "正文 V", "SYSTEM"));
        when(pushService.broadcast(eq(RealtimePushService.TYPE_NOTIFICATION), any())).thenReturn(5);

        RealtimeBroadcastRequest req = new RealtimeBroadcastRequest(
                "NOTIFICATION", null, "TPL_WS", Map.of("x", "V"));
        RealtimePushResultView result = adminService.broadcast(req);
        assertEquals(5, result.delivered());

        var typeCap = forClass(String.class);
        var payloadCap = forClass(Object.class);
        verify(pushService).broadcast(typeCap.capture(), payloadCap.capture());
        assertEquals(RealtimePushService.TYPE_NOTIFICATION, typeCap.getValue());
        RealtimeNotificationPayload p = (RealtimeNotificationPayload) payloadCap.getValue();
        assertEquals("标题", p.title());
        assertEquals("正文 V", p.content());
        assertEquals("SYSTEM", p.msgType());
    }

    @Test
    @DisplayName("模板路径：端口缺失时显式失败而非静默降级")
    void broadcastTemplatePortMissing() {
        when(templateProvider.getIfAvailable()).thenReturn(null);
        RealtimeBroadcastRequest req = new RealtimeBroadcastRequest(
                "NOTIFICATION", null, "TPL_WS", Map.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> adminService.broadcast(req));
        assertEquals(CommonErrorCode.CONFLICT, ex.getErrorCode());
        verifyNoInteractions(pushService);
    }

    @Test
    @DisplayName("定向推送：模板路径同样渲染为 NOTIFICATION 负载")
    void pushToUserTemplatePath() {
        RealtimeTemplatePort port = mock(RealtimeTemplatePort.class);
        when(templateProvider.getIfAvailable()).thenReturn(port);
        when(port.render(eq("TPL_WS"), any())).thenReturn(
                new RealtimeTemplateRenderResult("T", "C", "NOTICE"));
        when(pushService.sendToUsers(any(), eq(RealtimePushService.TYPE_NOTIFICATION), any())).thenReturn(2);

        RealtimeUserPushRequest req = new RealtimeUserPushRequest(
                List.of(1L, 2L), "NOTIFICATION", null, "TPL_WS", Map.of());
        RealtimePushResultView result = adminService.pushToUsers(req);
        assertEquals(2, result.delivered());
        verify(pushService).sendToUsers(eq(List.of(1L, 2L)), eq(RealtimePushService.TYPE_NOTIFICATION), any());
    }
}
