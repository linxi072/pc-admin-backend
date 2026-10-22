package com.acme.scaffold.system.notification.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.notification.dto.SendWithTemplateNotificationRequest;
import com.acme.scaffold.system.service.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationService} 单元测试：覆盖 M11 按模板发送的编排（取启用中模板 → 渲染 ${var}
 * → 按模板渠道走 {@code NotificationRouter} 路由到适配器）。模板服务以 mock 替身，
 * 渠道适配器用捕获型 fake，全离线、不依赖数据库与 Spring 上下文。
 */
class NotificationServiceTest {

    private NotificationTemplateService templateService;
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
        port = new CapturingPort();
        service = new NotificationService(List.of(port), templateService);
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
}
