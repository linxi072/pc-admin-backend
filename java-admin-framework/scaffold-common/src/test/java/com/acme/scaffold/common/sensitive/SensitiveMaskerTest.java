package com.acme.scaffold.common.sensitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏器单测（V1.1.0 测试覆盖重建立项：scaffold-common 批次）。
 * 覆盖统一分派入口 mask 与各策略的典型/边界形态。
 */
class SensitiveMaskerTest {

    @Test
    void maskPhoneShouldKeepHead3AndTail4() {
        assertEquals("138****1234", SensitiveMasker.maskPhone("13812341234"));
    }

    @Test
    void maskPhoneShouldReturnOriginalForNonPhone() {
        assertEquals("not-a-phone", SensitiveMasker.maskPhone("not-a-phone"));
        assertNull(SensitiveMasker.maskPhone(null));
    }

    @Test
    void maskEmailShouldKeepHeadAndDomain() {
        assertEquals("z***@example.com", SensitiveMasker.maskEmail("zhangsan@example.com"));
        assertEquals("plain", SensitiveMasker.maskEmail("plain"));
    }

    @Test
    void maskIdCardShouldKeepLast4() {
        assertEquals("**************1234", SensitiveMasker.maskIdCard("110101199001011234"));
        assertEquals("**************X234", SensitiveMasker.maskIdCard("11010119900101X234"));
    }

    @Test
    void maskBankCardShouldKeepLast4WithEqualStars() {
        assertEquals("************1234", SensitiveMasker.maskBankCard("622260026561234".substring(0, 16 - 4) + "1234"));
    }

    @Test
    void maskNameShouldKeepFirstCharOnly() {
        assertEquals("张**", SensitiveMasker.maskName("张三丰"));
        assertEquals("张", SensitiveMasker.maskName("张"));
    }

    @Test
    void maskAddressShouldKeepPrefix6() {
        assertEquals("北京市海淀区****", SensitiveMasker.maskAddress("北京市海淀区中关村大街1号"));
        assertEquals("短地址", SensitiveMasker.maskAddress("短地址"));
    }

    @Test
    void maskIpShouldKeepTwoSegments() {
        assertEquals("192.168.*.*", SensitiveMasker.maskIp("192.168.1.10"));
        assertEquals("******", SensitiveMasker.maskIp("unknown"));
        assertEquals("******", SensitiveMasker.maskIp("2001:db8::1"));
    }

    @Test
    void dispatchShouldHonorType() {
        assertEquals("138****1234", SensitiveMasker.mask("13812341234", SensitiveType.MOBILE));
        assertEquals("raw", SensitiveMasker.mask("raw", SensitiveType.NONE));
        assertSame(null, SensitiveMasker.mask(null, SensitiveType.MOBILE));
        assertEquals("raw", SensitiveMasker.mask("raw", null));
    }

    @Test
    void maskSummaryShouldEraseCredentialValues() {
        String out = SensitiveMasker.maskSummary("{\"password\":\"P@ssw0rd1\",\"name\":\"tom\"}");
        org.junit.jupiter.api.Assertions.assertFalse(out.contains("P@ssw0rd1"));
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("******"));
    }

    @Test
    void maskSummaryShouldMaskPhoneAndEmailInFreeText() {
        String out = SensitiveMasker.maskSummary("联系 13912345678 或 tom@example.com");
        org.junit.jupiter.api.Assertions.assertFalse(out.contains("13912345678"));
        org.junit.jupiter.api.Assertions.assertFalse(out.contains("tom@example.com"));
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("139****5678"));
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("t***@example.com"));
    }

    @Test
    void maskSummaryShouldBeIdempotentAndSafeForBlank() {
        assertEquals("", SensitiveMasker.maskSummary(""));
        assertEquals("   ", SensitiveMasker.maskSummary("   "));
        assertNull(SensitiveMasker.maskSummary(null));
        String once = SensitiveMasker.maskSummary("hi 13812341234");
        assertEquals(once, SensitiveMasker.maskSummary(once));
    }

    @Test
    void maskSummaryShouldEraseCommonCredentialAliases() {
        String text = "{\"apiKey\":\"ALIAS_API_KEY\",\"api_key\":\"ALIAS_SNAKE\","
                + "\"x-api-key\":\"ALIAS_X_API_KEY\",\"accessKey\":\"ALIAS_ACCESS\","
                + "\"secretKey\":\"ALIAS_SECRET_KEY\",\"idToken\":\"ALIAS_ID_TOKEN\","
                + "\"sessionId\":\"ALIAS_SESSION\",\"cookie\":\"ALIAS_COOKIE\","
                + "\"previewToken\":\"ALIAS_PREVIEW\",\"name\":\"tom\"}";

        String out = SensitiveMasker.maskSummary(text);

        for (String secret : new String[]{"ALIAS_API_KEY", "ALIAS_SNAKE", "ALIAS_X_API_KEY",
                "ALIAS_ACCESS", "ALIAS_SECRET_KEY", "ALIAS_ID_TOKEN", "ALIAS_SESSION",
                "ALIAS_COOKIE", "ALIAS_PREVIEW"}) {
            assertFalse(out.contains(secret), secret);
        }
        assertTrue(out.contains("\"name\":\"tom\""));
    }

    @Test
    void maskSummaryShouldMaskBankCardAndStayIdempotent() {
        String out = SensitiveMasker.maskSummary("卡号 6222600265612345 已提交");

        assertFalse(out.contains("6222600265612345"));
        assertTrue(out.contains("************2345"));
        assertEquals(out, SensitiveMasker.maskSummary(out));
    }

    @Test
    void maskSummaryShouldNotTreatMaskedIdCardAsBankCard() {
        String out = SensitiveMasker.maskSummary("身份证 110101199001011234");
        assertEquals("身份证 **************1234", out);
        assertEquals(out, SensitiveMasker.maskSummary(out));
    }
}
