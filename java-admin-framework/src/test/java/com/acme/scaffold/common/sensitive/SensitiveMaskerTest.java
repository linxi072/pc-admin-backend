package com.acme.scaffold.common.sensitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SensitiveMasker} 单元测试：数据对外可见性的最后一道防线，
 * 任何一条脱敏规则失效都等于把凭证/PII 暴露给管理员页面或导出文件，故逐规则锁定。
 *
 * <p>本测试由原 {@code common/audit/SensitiveMaskerTest} 迁移而来（包名随类迁移），
 * 既有断言<b>逐一保留</b>以证明迁移零行为变更，并追加新增策略（银行卡/姓名/地址）与统一分派入口的断言。</p>
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

    // ---------------- 新增策略：银行卡 / 姓名 / 地址 ----------------

    @Test
    void masksBankCardKeepingLast4AndLength() {
        // 16 位卡号：保留后 4 位，其余等长掩码（位数可用于核对）
        assertEquals("************7890", SensitiveMasker.maskBankCard("6222021234567890"));
        // 19 位卡号同样支持
        assertEquals("***************0123", SensitiveMasker.maskBankCard("6222021234567890123"));
        // 11 位及以下（如普通编号）不视为卡号
        assertEquals("12345678901", SensitiveMasker.maskBankCard("12345678901"));
        // 带空格的展示型卡号非「连续数字」形态，原样返回（避免误伤）
        assertEquals("6222 0212 3456 7890", SensitiveMasker.maskBankCard("6222 0212 3456 7890"));
    }

    @Test
    void masksNameKeepingFirstChar() {
        assertEquals("张**", SensitiveMasker.maskName("张三丰"));
        assertEquals("张*", SensitiveMasker.maskName("张三"));
        // 单字符与空白无从掩码，原样返回（空白做 trim）
        assertEquals("张", SensitiveMasker.maskName("张"));
        assertEquals("   ", SensitiveMasker.maskName("   "));
    }

    @Test
    void masksAddressKeepingPrefix() {
        assertEquals("北京市朝阳区****", SensitiveMasker.maskAddress("北京市朝阳区建国路1号院2单元"));
        // 短地址不掩码，避免整串变成无意义掩码
        assertEquals("北京路1号", SensitiveMasker.maskAddress("北京路1号"));
    }

    // ---------------- 统一分派 ----------------

    @Test
    void dispatchesByType() {
        assertEquals("138****1234", SensitiveMasker.mask("13812341234", SensitiveType.MOBILE));
        assertEquals("z***@example.com", SensitiveMasker.mask("zhangsan@example.com", SensitiveType.EMAIL));
        assertEquals("**************002X", SensitiveMasker.mask("11010519491231002X", SensitiveType.ID_CARD));
        assertEquals("192.168.*.*", SensitiveMasker.mask("192.168.1.23", SensitiveType.IP));
        assertEquals("张**", SensitiveMasker.mask("张三丰", SensitiveType.NAME));
        assertEquals("************7890", SensitiveMasker.mask("6222021234567890", SensitiveType.BANK_CARD));
        assertEquals("北京市朝阳区****", SensitiveMasker.mask("北京市朝阳区建国路1号院", SensitiveType.ADDRESS));
    }

    @Test
    void dispatchWithNullTypeOrNoneLeavesValueUnchanged() {
        // null 类型视为不脱敏
        assertEquals("13812341234", SensitiveMasker.mask("13812341234", null));
        // 显式 NONE 不脱敏
        assertEquals("13812341234", SensitiveMasker.mask("13812341234", SensitiveType.NONE));
        // null 值穿透
        assertNull(SensitiveMasker.mask(null, SensitiveType.MOBILE));
    }

    @Test
    void maskingIsIdempotent() {
        // 已脱敏文本再次脱敏不应被二次破坏（各策略对「非匹配形态」原样返回）
        String once = SensitiveMasker.mask("13812341234", SensitiveType.MOBILE);
        assertEquals(once, SensitiveMasker.mask(once, SensitiveType.MOBILE));
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
        assertNull(SensitiveMasker.maskBankCard(null));
        assertNull(SensitiveMasker.maskName(null));
        assertNull(SensitiveMasker.maskAddress(null));
    }

    @Test
    void nonSensitiveTextIsUnchanged() {
        String raw = "{\"title\":\"公告\",\"status\":\"PUBLISHED\",\"id\":7}";
        assertEquals(raw, SensitiveMasker.maskSummary(raw));
    }
}
