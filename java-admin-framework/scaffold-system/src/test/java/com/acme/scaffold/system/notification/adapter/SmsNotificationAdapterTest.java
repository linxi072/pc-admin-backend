package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.config.SmsProperties;
import com.acme.scaffold.system.notification.port.SmsGateway;
import com.acme.scaffold.system.notification.rules.SmsRules;
import com.acme.scaffold.system.smslog.SmsRecordRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SmsNotificationAdapter} 测试：仅支持 SMS；未启用抛校验异常且不外发；
 * 启用后按规则归一（含签名拼接）并投递，返回接收号码数；
 * 发送结果（成功/网关失败）均落一条发送记录，记录写入不改变投递语义。
 */
class SmsNotificationAdapterTest {

    private final SmsGateway gateway = mock(SmsGateway.class);
    private final SmsRecordRecorder recorder = mock(SmsRecordRecorder.class);

    private SmsProperties props(boolean enabled) {
        SmsProperties p = new SmsProperties();
        p.setEnabled(enabled);
        p.setSignature("【Acme】");
        return p;
    }

    private SmsNotificationAdapter adapter(boolean enabled) {
        return new SmsNotificationAdapter(gateway, props(enabled), recorder);
    }

    private NotificationMessage sms(List<String> mobiles) {
        return new NotificationMessage(Channel.SMS, "忽略的标题", "您的验证码是 123456", "ALERT",
                null, null, null, null, null, null, mobiles);
    }

    @Test
    void supportsSmsOnly() {
        SmsNotificationAdapter a = adapter(true);
        assertTrue(a.supports(Channel.SMS));
        assertEquals(false, a.supports(Channel.IN_APP));
        assertEquals(false, a.supports(Channel.WEBHOOK));
        assertEquals(false, a.supports(Channel.EMAIL));
        assertEquals(false, a.supports(Channel.WEBSOCKET));
    }

    @Test
    void dispatchSendsNormalizedDraft() {
        SmsNotificationAdapter a = adapter(true);
        NotificationMessage msg = new NotificationMessage(Channel.SMS, null, "您的验证码是 123456", null,
                null, null, null, null, null, null,
                List.of(" +8613800138000 ", "13800138000", "13900139000"));

        assertEquals(2, a.dispatch(msg));
        verify(gateway).send(new SmsRules.SmsDraft(
                List.of("13800138000", "13900139000"), "【Acme】您的验证码是 123456"));
    }

    @Test
    void dispatchThrowsWhenDisabled() {
        SmsNotificationAdapter a = adapter(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> a.dispatch(sms(List.of("13800138000"))));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(gateway, never()).send(any());
        // 未尝试发送，不产生记录
        verify(recorder, never()).record(any(), any(), any(), any(), eq(true), isNull());
    }

    @Test
    void dispatchThrowsWhenMobilesMissing() {
        SmsNotificationAdapter a = adapter(true);
        assertThrows(BusinessException.class, () -> a.dispatch(sms(null)));
        assertThrows(BusinessException.class, () -> a.dispatch(sms(List.of("  "))));
        verify(gateway, never()).send(any());
        verify(recorder, never()).record(any(), any(), any(), any(), eq(true), isNull());
    }

    @Test
    void dispatchThrowsWhenMobileIllegal() {
        SmsNotificationAdapter a = adapter(true);
        assertThrows(BusinessException.class,
                () -> a.dispatch(sms(List.of("13800138000", "12345"))));
        verify(gateway, never()).send(any());
    }

    @Test
    void dispatchThrowsWhenContentBlank() {
        SmsNotificationAdapter a = adapter(true);
        NotificationMessage msg = new NotificationMessage(Channel.SMS, "t", "  ", null,
                null, null, null, null, null, null, List.of("13800138000"));
        assertThrows(BusinessException.class, () -> a.dispatch(msg));
        verify(gateway, never()).send(any());
    }

    @Test
    void dispatchRecordsSuccessAfterSend() {
        SmsNotificationAdapter a = adapter(true);
        a.dispatch(sms(List.of("13800138000", "13900139000")));

        // 成功路径：号码为归一后列表、正文为签名拼接结果、provider 取配置枚举名
        verify(recorder).record(any(),
                eq(List.of("13800138000", "13900139000")),
                eq("【Acme】您的验证码是 123456"),
                eq("LOG"), eq(true), isNull());
    }

    @Test
    void dispatchRecordsFailureAndRethrowsWhenGatewayFails() {
        SmsNotificationAdapter a = adapter(true);
        doThrow(new IllegalStateException("gateway down")).when(gateway).send(any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> a.dispatch(sms(List.of("13800138000"))));
        assertEquals("gateway down", ex.getMessage());
        // 失败同样留痕（状态 FAILED + 异常消息），再向上抛出
        verify(recorder).record(any(),
                eq(List.of("13800138000")),
                eq("【Acme】您的验证码是 123456"),
                eq("LOG"), eq(false), eq("gateway down"));
    }
}
