package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.system.notification.rules.SmsRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link LoggingSmsGateway} 测试：开发默认通道仅记录脱敏日志，不得抛异常、
 * 不得触达任何外部系统（手机号脱敏断言由 {@code SensitiveMaskerTest} 覆盖）。
 */
class LoggingSmsGatewayTest {

    private final LoggingSmsGateway gateway = new LoggingSmsGateway();

    @Test
    void sendLogsWithoutTouchingNetwork() {
        SmsRules.SmsDraft draft = new SmsRules.SmsDraft(
                List.of("13800138000", "13900139000"), "【Acme】您的验证码是 123456");
        // 不抛异常即通过：验证通道对任意合法草稿可消费
        assertDoesNotThrow(() -> gateway.send(draft));
    }

    @Test
    void sendHandlesSingleMobile() {
        SmsRules.SmsDraft draft = new SmsRules.SmsDraft(List.of("13800138000"), "内容");
        assertDoesNotThrow(() -> gateway.send(draft));
    }
}
