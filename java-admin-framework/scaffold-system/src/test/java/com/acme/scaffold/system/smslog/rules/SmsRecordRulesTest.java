package com.acme.scaffold.system.smslog.rules;

import com.acme.scaffold.system.notification.config.SmsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SmsRecordRules} 测试：脱敏（号码掩码/正文摘要）与筛选值归一（状态/网关白名单）。
 *
 * <p>这些纯函数决定「存什么」与「查什么」的口径，是短信记录模块的正确性核心，
 * 必须与 {@code SensitiveMasker} 掩码形态、{@code SmsProperties.Provider} 枚举保持同源。
 */
class SmsRecordRulesTest {

    // ---------- maskMobile / maskMobiles ----------

    @Test
    void maskMobile_masksMiddleFourDigits() {
        assertEquals("138****8000", SmsRecordRules.maskMobile("13800138000"));
        assertEquals("138****8000", SmsRecordRules.maskMobile(" 13800138000 "));
    }

    @Test
    void maskMobile_passesThroughNonPhoneVerbatim() {
        // 非号码形态（含 +86 前缀的国际形态）原样返回，保证审计可读而非制造假数据
        assertEquals("+8613800138000", SmsRecordRules.maskMobile("+8613800138000"));
        assertEquals("12345", SmsRecordRules.maskMobile("12345"));
    }

    @Test
    void maskMobile_nullBecomesEmpty() {
        assertEquals("", SmsRecordRules.maskMobile(null));
    }

    @Test
    void maskMobiles_joinsInOrder() {
        // 掩码形态：保前 3 位 + **** + 保后 4 位（SensitiveMasker.maskPhone 口径）
        assertEquals("138****8000,139****9000",
                SmsRecordRules.maskMobiles(List.of("13800138000", "13900139000")));
    }

    @Test
    void maskMobiles_emptyForNullOrEmpty() {
        assertEquals("", SmsRecordRules.maskMobiles(null));
        assertEquals("", SmsRecordRules.maskMobiles(List.of()));
    }

    // ---------- summarizeContent ----------

    @Test
    void summarizeContent_keepsShortTextVerbatim() {
        assertEquals("您的验证码是 123456", SmsRecordRules.summarizeContent("您的验证码是 123456"));
    }

    @Test
    void summarizeContent_foldsControlCharsAndCollapsesSpaces() {
        assertEquals("第一行 第二行", SmsRecordRules.summarizeContent("第一行\r\n第二行"));
        assertEquals("a b", SmsRecordRules.summarizeContent("a \t\n b"));
        assertEquals("trim 后", SmsRecordRules.summarizeContent("  trim  后  "));
    }

    @Test
    void summarizeContent_truncatesWithEllipsis() {
        String out = SmsRecordRules.summarizeContent("长".repeat(SmsRecordRules.MAX_SUMMARY_LEN + 50));
        assertEquals(SmsRecordRules.MAX_SUMMARY_LEN + 1, out.length());
        assertTrue(out.endsWith("…"));
        assertTrue(out.startsWith("长".repeat(SmsRecordRules.MAX_SUMMARY_LEN)));
    }

    @Test
    void summarizeContent_emptyForBlank() {
        assertEquals("", SmsRecordRules.summarizeContent(null));
        assertEquals("", SmsRecordRules.summarizeContent("   "));
        assertEquals("", SmsRecordRules.summarizeContent("\r\n\t"));
    }

    // ---------- normalizeStatus ----------

    @Test
    void normalizeStatus_acceptsKnownValuesCaseInsensitive() {
        assertEquals("SUCCESS", SmsRecordRules.normalizeStatus("success"));
        assertEquals("FAILED", SmsRecordRules.normalizeStatus(" failed "));
    }

    @Test
    void normalizeStatus_rejectsUnknownAsNull() {
        assertNull(SmsRecordRules.normalizeStatus("PENDING"));
        assertNull(SmsRecordRules.normalizeStatus(""));
        assertNull(SmsRecordRules.normalizeStatus(null));
    }

    // ---------- normalizeProvider ----------

    @Test
    void normalizeProvider_acceptsEnumValuesCaseInsensitive() {
        assertEquals("LOG", SmsRecordRules.normalizeProvider("log"));
        assertEquals("HTTP", SmsRecordRules.normalizeProvider(" Http "));
    }

    @Test
    void normalizeProvider_rejectsUnknownAsNull() {
        assertNull(SmsRecordRules.normalizeProvider("ALIYUN"));
        assertNull(SmsRecordRules.normalizeProvider(null));
    }

    @Test
    void normalizeProvider_tracksEnumExtension() {
        // 枚举新增实现时本方法自动放行（无需改代码），此处固化当前枚举规模
        assertEquals(2, SmsProperties.Provider.values().length);
    }

    // ---------- truncate ----------

    @Test
    void truncate_capsAtMax() {
        assertEquals("abcde", SmsRecordRules.truncate("abcdefg", 5));
        assertEquals("abc", SmsRecordRules.truncate("abc", 5));
        assertEquals("abc", SmsRecordRules.truncate("abc", 3));
    }

    @Test
    void truncate_nullAndNonPositiveMax() {
        assertNull(SmsRecordRules.truncate(null, 10));
        assertEquals("", SmsRecordRules.truncate("abc", 0));
        assertEquals("", SmsRecordRules.truncate("abc", -1));
    }
}
