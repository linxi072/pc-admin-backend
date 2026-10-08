package com.acme.scaffold.common.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SensitiveMasker} 单元测试：审计日志对外可见性的最后一道防线，
 * 任何一条脱敏规则失效都等于把凭证/PII 暴露给管理员页面，故逐规则锁定。
 */
class SensitiveMaskerTest {

    // ---------------- 凭证类键名 ----------------

    @Test
    void masksPasswordStringValue() {
        String raw = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";
        String masked = SensitiveMasker.maskSummary(raw);
        assertTrue(masked.contains("\"password\":\"******\""), masked);
        assertTrue(masked.contains("\"username\":\"admin\""), masked);
    }

    @Test
    void masksTokenAndSecretRegardlessOfCase() {
        String raw = "{\"AccessToken\":\"a.b.c\",\"refresh_token\":\"xyz\",\"clientSecret\":\"s3cr3t\"}";
        String masked = SensitiveMasker.maskSummary(raw);
        assertFalse(masked.contains("a.b.c"), masked);
        assertFalse(masked.contains("xyz"), masked);
        assertFalse(masked.contains("s3cr3t"), masked);
    }

    @Test
    void masksLiteralCredentialValue() {
        String raw = "{\"pin\":123456,\"captcha\":null}";
        String masked = SensitiveMasker.maskSummary(raw);
        assertFalse(masked.contains("123456"), masked);
        assertTrue(masked.contains("\"captcha\":\"******\""), masked);
    }

    @Test
    void masksValueContainingEscapedQuote() {
        String raw = "{\"password\":\"a\\\"b\"}";
        String masked = SensitiveMasker.maskSummary(raw);
        assertFalse(masked.contains("a\\\"b"), masked);
    }

    // ---------------- PII ----------------

    @Test
    void masksPhoneKeepingPrefixAndSuffix() {
        assertEquals("138****1234", SensitiveMasker.maskPhone("13812341234"));
        // 自由文本中的手机号同样脱敏，且不影响前后普通文本
        assertEquals("联系手机 138****1234 请核实", SensitiveMasker.maskSummary("联系手机 13812341234 请核实"));
    }

    @Test
    void doesNotMaskNonPhoneDigits() {
        // 12 位订单号不应被当作手机号处理（前后都是数字时直接跳过）
        assertEquals("138123412345", SensitiveMasker.maskSummary("138123412345"));
        assertEquals("12345678901", SensitiveMasker.maskPhone("12345678901"));
    }

    @Test
    void masksEmailKeepingHeadAndDomain() {
        assertEquals("z***@example.com", SensitiveMasker.maskEmail("zhangsan@example.com"));
        assertEquals("邮箱 z***@example.com", SensitiveMasker.maskSummary("邮箱 zhangsan@example.com"));
    }

    @Test
    void masksIdCardKeepingLast4() {
        assertEquals("**************002X", SensitiveMasker.maskIdCard("11010519491231002X"));
        assertEquals("身份证 **************002X", SensitiveMasker.maskSummary("身份证 11010519491231002X"));
    }

    // ---------------- IP ----------------

    @Test
    void masksIpv4KeepingNetworkSegment() {
        assertEquals("192.168.*.*", SensitiveMasker.maskIp("192.168.1.23"));
    }

    @Test
    void masksNonIpv4Entirely() {
        assertEquals("******", SensitiveMasker.maskIp("2001:db8::1"));
        assertEquals("******", SensitiveMasker.maskIp("unknown"));
    }

    // ---------------- 边界 ----------------

    @Test
    void nullAndBlankAreReturnedAsIs() {
        assertNull(SensitiveMasker.maskSummary(null));
        // 空白串不做无谓处理，原样返回（与 null 区分，避免调用方误判）
        assertEquals("   ", SensitiveMasker.maskSummary("   "));
        assertNull(SensitiveMasker.maskPhone(null));
        assertNull(SensitiveMasker.maskEmail(null));
        assertNull(SensitiveMasker.maskIdCard(null));
        assertNull(SensitiveMasker.maskIp(null));
    }

    @Test
    void nonSensitiveTextIsUnchanged() {
        String raw = "{\"title\":\"公告\",\"status\":\"PUBLISHED\",\"id\":7}";
        assertEquals(raw, SensitiveMasker.maskSummary(raw));
    }
}
