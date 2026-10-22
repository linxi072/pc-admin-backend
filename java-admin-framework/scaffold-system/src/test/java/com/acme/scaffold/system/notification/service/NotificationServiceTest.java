package com.acme.scaffold.system.notification.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.notification.dto.SendWithTemplateNotificationRequest;
import com.acme.scaffold.system.notifylog.NotificationRecordRecorder;
import com.acme.scaffold.system.notifypref.NotificationPreferenceService;
import com.acme.scaffold.system.notifypref.rules.NotificationPreferenceRules;
import com.acme.scaffold.system.service.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationService} 单元测试：覆盖 M11 按模板发送的编排（取启用中模板 → 渲染 ${var}
 * → 按模板渠道走 {@code NotificationRouter} 路由到适配器）、M12 发送记录旁路
 * （dispatch 成功/失败均留痕、templateCode 透传）与 M13 偏好过滤挂点
 * （过滤后消息才进适配器、全部退订记 SKIPPED 不调端口、偏好查询异常按 FAILED 处理）。
 * 模板服务、记录器与偏好服务以 mock 替身，渠道适配器用捕获型 fake，全离线、
 * 不依赖数据库与 Spring 上下文。
 */
class NotificationServiceTest {

    private NotificationTemplateService templateService;
    private NotificationRecordRecorder recordRecorder;
    private NotificationPreferenceService preferenceService;
    private CapturingPort port;
    private NotificationService service;

    /** 捕获被投递消息的测试适配器（仅支持 EMAIL）。 */
    static final class CapturingPort implements NotificationPort {
        NotificationMessage last;
        int ret = 1;

        @Override
        public boolean supports(Channel channel) {
            return channel == Channel.EMAIL;
        }

        @Override
        public int dispatch(NotificationMessage message) {
            this.last = message;
            return ret;
        }
    }

    private static SysNotificationTemplateDO template(String title, String content, String channel, Integer enabled) {
        SysNotificationTemplateDO d = new SysNotificationTemplateDO();
        d.setId(1L);
        d.setTemplateCode("ORDER_SHIPPED");
        d.setTemplateName("发货通知");
        d.setChannel(channel);
        d.setTitle(title);
        d.setContent(content);
        d.setMsgType("NOTICE");
        d.setEnabled(enabled);
        return d;
    }

    @BeforeEach
    void setUp() {
        templateService = mock(NotificationTemplateService.class);
        recordRecorder = mock(NotificationRecordRecorder.class);
        preferenceService = mock(NotificationPreferenceService.class);
        port = new CapturingPort();
        service = new NotificationService(List.of(port), templateService, recordRecorder,
                preferenceService);
        // 默认不过滤：原样返回入参消息（覆盖 M11/M12 各例的既有语义）
        when(preferenceService.applyPreferences(any(NotificationMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("sendWithTemplate：渲染 ${var} 后按模板渠道/标题/正文投递")
    void sendWithTemplate_rendersAndDispatches() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("订单 ${orderNo} 已发货", "尊敬的会员，订单 ${orderNo} 已发货。", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of("orderNo", "NO2026"),
                null, null, null, null, List.of("a@x.com"), null);

        int delivered = service.sendWithTemplate(req);

        assertEquals(1, delivered);
        assertEquals(Channel.EMAIL, port.last.channel());
        assertEquals("订单 NO2026 已发货", port.last.title());
        assertEquals("尊敬的会员，订单 NO2026 已发货。", port.last.content());
        assertEquals("NOTICE", port.last.msgType());
        assertEquals(List.of("a@x.com"), port.last.emails());
    }

    @Test
    @DisplayName("sendWithTemplate：未提供变量时 ${var} 保持原样（SMS 模板标题可为空）")
    void sendWithTemplate_missingVarsKeptAndNullTitle() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template(null, "订单 ${orderNo} 已发货", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", null, null, null, null, null, List.of("a@x.com"), null);

        service.sendWithTemplate(req);

        assertNull(port.last.title());
        assertEquals("订单 ${orderNo} 已发货", port.last.content());
    }

    @Test
    @DisplayName("sendWithTemplate：模板缺失/停用异常向上传播，不投递")
    void sendWithTemplate_propagatesTemplateError() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "通知模板不存在: ORDER_SHIPPED"));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("a@x.com"), null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendWithTemplate(req));
        assertTrue(ex.getMessage().contains("模板不存在"));
    }

    @Test
    @DisplayName("sendWithTemplate：模板渠道无适配器时显式失败（不静默丢弃）")
    void sendWithTemplate_unsupportedChannelThrows() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "SMS", 1)); // 仅注册了 EMAIL 适配器
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, null, List.of("13800000000"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.sendWithTemplate(req));
        assertTrue(ex.getMessage().contains("不支持的通知渠道"));
    }

    // ------------------------------------------------------------------
    // M12：发送记录旁路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：投递成功记录 SUCCESS + 实际投递数，原始直发 templateCode 为 null")
    void dispatch_successRecords() {
        port.ret = 2;

        int delivered = service.dispatch(new NotificationMessage(Channel.EMAIL, "标题", "正文"));

        assertEquals(2, delivered);
        verify(recordRecorder).record(any(NotificationMessage.class), isNull(), eq(2), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：渠道无适配器时记录 FAILED + 异常消息并原样重抛")
    void dispatch_failureRecordsAndRethrows() {
        NotificationMessage msg = new NotificationMessage(Channel.SMS, null, "正文");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(msg));

        assertTrue(ex.getMessage().contains("不支持的通知渠道"));
        verify(recordRecorder).record(eq(msg), isNull(), eq(0), eq(false),
                contains("不支持的通知渠道"));
    }

    @Test
    @DisplayName("sendWithTemplate：模板编码透传到发送记录（provenance 审计）")
    void sendWithTemplate_templateCodePropagatedToRecord() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("订单已发货", "尊敬的会员，订单已发货。", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("a@x.com"), null);

        service.sendWithTemplate(req);

        verify(recordRecorder).record(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(1), eq(true), isNull());
    }

    @Test
    @DisplayName("sendWithTemplate：投递失败同样记录 FAILED（含模板编码）并向上传播")
    void sendWithTemplate_failureRecordsWithTemplateCode() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "SMS", 1)); // 仅注册了 EMAIL 适配器
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, null, List.of("13800000000"));

        assertThrows(BusinessException.class, () -> service.sendWithTemplate(req));

        verify(recordRecorder).record(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(0), eq(false), contains("不支持的通知渠道"));
    }

    // ------------------------------------------------------------------
    // M13：偏好过滤挂点
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dispatch：偏好过滤后的消息才进适配器，记录仍以原始消息为审计对象")
    void dispatch_preferenceFilteredMessageIsDispatched() {
        NotificationMessage original = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com", "b@x.com"), null);
        NotificationMessage filtered = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "NOTICE", null, null, null, null, null, List.of("a@x.com"), null);
        when(preferenceService.applyPreferences(original)).thenReturn(filtered);

        int delivered = service.dispatch(original);

        assertEquals(1, delivered);
        assertSame(filtered, port.last);
        // 记录旁路以原始消息为对象：目标接收数反映发送意图，退订差异由状态码表达
        verify(recordRecorder).record(eq(original), isNull(), eq(1), eq(true), isNull());
    }

    @Test
    @DisplayName("dispatch：定向接收者全部退订 → 记 SKIPPED、不调适配器、静默返回 0")
    void dispatch_allUnsubscribedSkipsPortAndRecordsSkipped() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文",
                "ALERT", null, null, null, null, null, List.of("opted.out@x.com"), null);
        when(preferenceService.applyPreferences(msg)).thenReturn(null);

        int delivered = service.dispatch(msg);

        assertEquals(0, delivered);
        assertNull(port.last);
        // port 为手写捕获型 fake（非 Mockito mock），以「无消息进入适配器」断言替代 never() 验证
        verify(recordRecorder).skip(eq(msg), isNull(),
                eq(NotificationPreferenceRules.REASON_ALL_UNSUBSCRIBED));
        // 退订不是故障：不得落入 FAILED 记录
        verify(recordRecorder, never()).record(any(), any(), anyInt(), eq(false), any());
    }

    @Test
    @DisplayName("dispatch：偏好查询异常按 FAILED 记录并向上传播（不静默吞掉）")
    void dispatch_preferenceLookupFailureRecordsFailed() {
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL, "标题", "正文");
        when(preferenceService.applyPreferences(msg))
                .thenThrow(new IllegalStateException("偏好查询失败"));

        RuntimeException ex = assertThrows(IllegalStateException.class, () -> service.dispatch(msg));

        assertEquals("偏好查询失败", ex.getMessage());
        assertNull(port.last);
        verify(recordRecorder).record(eq(msg), isNull(), eq(0), eq(false), eq("偏好查询失败"));
    }

    @Test
    @DisplayName("sendWithTemplate：模板发送同样经过偏好过滤（模板编码随 SKIPPED 回填）")
    void sendWithTemplate_preferenceHookAppliesWithTemplateCode() {
        when(templateService.requireEnabledByCode("ORDER_SHIPPED"))
                .thenReturn(template("t", "c", "EMAIL", 1));
        SendWithTemplateNotificationRequest req = new SendWithTemplateNotificationRequest(
                "ORDER_SHIPPED", Map.of(), null, null, null, null, List.of("opted.out@x.com"), null);
        when(preferenceService.applyPreferences(any(NotificationMessage.class))).thenReturn(null);

        int delivered = service.sendWithTemplate(req);

        assertEquals(0, delivered);
        assertNull(port.last);
        verify(recordRecorder).skip(any(NotificationMessage.class), eq("ORDER_SHIPPED"),
                eq(NotificationPreferenceRules.REASON_ALL_UNSUBSCRIBED));
    }
}
