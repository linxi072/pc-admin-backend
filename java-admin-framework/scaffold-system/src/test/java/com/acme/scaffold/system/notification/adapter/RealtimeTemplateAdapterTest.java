package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.port.RealtimeTemplateRenderResult;
import com.acme.scaffold.system.entity.SysNotificationTemplateDO;
import com.acme.scaffold.system.service.NotificationTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RealtimeTemplateAdapter} 单测（M30 模板化系统侧适配器）。
 *
 * <p>用 Mockito 桩接 {@link NotificationTemplateService#requireEnabledByCode}，
 * 验证：WEBSOCKET 模板渲染、非 WEBSOCKET 渠道拒绝、变量缺省时保留 {@code ${name}} 原样。</p>
 */
class RealtimeTemplateAdapterTest {

    private final NotificationTemplateService templateService = mock(NotificationTemplateService.class);
    private final RealtimeTemplateAdapter adapter = new RealtimeTemplateAdapter(templateService);

    @Test
    @DisplayName("WEBSOCKET 模板：用变量渲染标题 / 正文 / 消息类型")
    void renderWebsocketTemplate() {
        SysNotificationTemplateDO tpl = new SysNotificationTemplateDO();
        tpl.setChannel("WEBSOCKET");
        tpl.setTitle("你好 ${name}");
        tpl.setContent("订单 ${orderId} 已发货");
        tpl.setMsgType("SYSTEM");
        tpl.setEnabled(1);
        when(templateService.requireEnabledByCode("TPL_WS")).thenReturn(tpl);

        RealtimeTemplateRenderResult r = adapter.render("TPL_WS",
                Map.of("name", "Alice", "orderId", "A100"));
        assertEquals("你好 Alice", r.title());
        assertEquals("订单 A100 已发货", r.content());
        assertEquals("SYSTEM", r.msgType());
    }

    @Test
    @DisplayName("非 WEBSOCKET 渠道模板：显式拒绝")
    void rejectNonWebsocketChannel() {
        SysNotificationTemplateDO tpl = new SysNotificationTemplateDO();
        tpl.setChannel("EMAIL");
        tpl.setTitle("t");
        tpl.setContent("c");
        tpl.setMsgType("NOTICE");
        tpl.setEnabled(1);
        when(templateService.requireEnabledByCode("TPL_EMAIL")).thenReturn(tpl);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adapter.render("TPL_EMAIL", Map.of()));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("变量缺省：未提供取值时保持 ${name} 原样")
    void missingVariablesKeptAsPlaceholder() {
        SysNotificationTemplateDO tpl = new SysNotificationTemplateDO();
        tpl.setChannel("WEBSOCKET");
        tpl.setTitle("Hi ${name}");
        tpl.setContent("c");
        tpl.setMsgType("NOTICE");
        tpl.setEnabled(1);
        when(templateService.requireEnabledByCode("TPL_WS")).thenReturn(tpl);

        RealtimeTemplateRenderResult r = adapter.render("TPL_WS", null);
        assertEquals("Hi ${name}", r.title());
        assertEquals("c", r.content());
    }
}
