package com.acme.scaffold.system.notification.rules;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SmsRules} 单元测试：号码校验矩阵（含国家码/分隔符归一）、
 * 号码列表归一与上限、内容长度约束、签名拼接与净化。纯函数离线验证，不触达网关。
 */
class SmsRulesTest {

    @Test
    void validMobilesAccepted() {
        assertTrue(SmsRules.isValidMobile("13800138000"));
        assertTrue(SmsRules.isValidMobile("19912345678"));
        assertTrue(SmsRules.isValidMobile("  13800138000  ")); // trim 后合法
        assertTrue(SmsRules.isValidMobile("+8613800138000")); // 国家码前缀剥离
        assertTrue(SmsRules.isValidMobile("8613800138000")); // 86 前缀（13 位）剥离
        assertTrue(SmsRules.isValidMobile("138-0013-8000")); // 连字符分隔剥离
        assertTrue(SmsRules.isValidMobile("138 0013 8000")); // 空格分隔剥离
    }

    @Test
    void invalidMobilesRejected() {
        assertFalse(SmsRules.isValidMobile(null));
        assertFalse(SmsRules.isValidMobile(""));
        assertFalse(SmsRules.isValidMobile("   "));
        assertFalse(SmsRules.isValidMobile("12345678901")); // 第二位 2 不在 3-9
        assertFalse(SmsRules.isValidMobile("1380013800")); // 少一位
        assertFalse(SmsRules.isValidMobile("138001380000")); // 多一位
        assertFalse(SmsRules.isValidMobile("23800138000")); // 首位非 1
        assertFalse(SmsRules.isValidMobile("1380013800a")); // 含字母
        assertFalse(SmsRules.isValidMobile("861380013800")); // 86 前缀但总长非 13，剥离后非 11 位
        assertNull(SmsRules.normalizeMobile("abc"));
    }

    @Test
    void normalizeMobileStripsPrefixAndSeparators() {
        assertEquals("13800138000", SmsRules.normalizeMobile("+86 138-0013-8000"));
        assertEquals("13800138000", SmsRules.normalizeMobile("8613800138000"));
    }

    @Test
    void normalizeMobilesTrimsDedupesAndRejectsEmpty() {
        List<String> normalized = SmsRules.normalizeMobiles(
                List.of(" 13800138000 ", "13900139000", "13800138000", "", "   ", "+8613900139000"));
        assertEquals(List.of("13800138000", "13900139000"), normalized);

        assertThrows(BusinessException.class, () -> SmsRules.normalizeMobiles(null));
        assertThrows(BusinessException.class, () -> SmsRules.normalizeMobiles(List.of()));
        assertThrows(BusinessException.class, () -> SmsRules.normalizeMobiles(List.of("  ")));
    }

    @Test
    void normalizeMobilesRejectsIllegalEntry() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SmsRules.normalizeMobiles(List.of("13800138000", "12345")));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("12345"));
    }

    @Test
    void normalizeMobilesRejectsOverLimit() {
        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= SmsRules.MAX_MOBILES; i++) {
            // 生成 101 个互不相同的合法号码（前三位固定，变化后八位）
            tooMany.add("138" + String.format("%08d", i));
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SmsRules.normalizeMobiles(tooMany));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains(String.valueOf(SmsRules.MAX_MOBILES)));
    }

    @Test
    void validateAndNormalizeRejectsBlankOrOverlongContent() {
        List<String> mobiles = List.of("13800138000");
        assertThrows(BusinessException.class, () -> SmsRules.validateAndNormalize(mobiles, null, null));
        assertThrows(BusinessException.class, () -> SmsRules.validateAndNormalize(mobiles, "   ", null));
        assertThrows(BusinessException.class,
                () -> SmsRules.validateAndNormalize(mobiles, "x".repeat(SmsRules.MAX_CONTENT_LEN + 1), null));
    }

    @Test
    void validateAndNormalizeBuildsCleanDraft() {
        SmsRules.SmsDraft draft = SmsRules.validateAndNormalize(
                List.of(" +8613800138000 ", "13800138000", "13900139000"),
                "您的验证码是 123456",
                "【Acme】");

        assertEquals(List.of("13800138000", "13900139000"), draft.mobiles());
        assertEquals("【Acme】您的验证码是 123456", draft.content());
    }

    @Test
    void appendSignatureSanitizesAndWraps() {
        // 空签名原样返回
        assertEquals("内容", SmsRules.appendSignature("内容", null));
        assertEquals("内容", SmsRules.appendSignature("内容", "   "));
        // 已带方括号不重复包裹
        assertEquals("【Acme】内容", SmsRules.appendSignature("内容", "【Acme】"));
        // 缺方括号补齐
        assertEquals("【Acme】内容", SmsRules.appendSignature("内容", "Acme"));
        // 半开括号剥离后补齐（防重复包裹）
        assertEquals("【Acme】内容", SmsRules.appendSignature("内容", "【Acme"));
        // CR/LF 剔除，防网关报文注入
        assertEquals("【Acme】内容", SmsRules.appendSignature("内容", "Ac\r\nme"));
        // 超长截断：签名截到 32 字后包方括号（+2）再加正文「内容」（+2）
        assertEquals(SmsRules.MAX_SIGNATURE_LEN + 4,
                SmsRules.appendSignature("内容", "长".repeat(SmsRules.MAX_SIGNATURE_LEN + 10)).length());
    }
}
