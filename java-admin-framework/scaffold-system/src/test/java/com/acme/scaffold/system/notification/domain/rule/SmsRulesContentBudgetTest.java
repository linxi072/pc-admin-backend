package com.acme.scaffold.system.notification.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 短信长度预算回归：正文上限必须按「含签名」的最终报文计算，签名自身也不得突破含括号上限。 */
class SmsRulesContentBudgetTest {

    private static final List<String> MOBILES = List.of("13800138000");

    @Test
    void shouldRejectWhenSignaturePushesContentOverLimit() {
        String content = "a".repeat(SmsRules.MAX_CONTENT_LEN);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> SmsRules.validateAndNormalize(MOBILES, content, "【Acme】"));

        assertTrue(ex.getMessage().contains("短信内容（含签名）超过长度上限"));
        assertTrue(ex.getMessage().contains(String.valueOf(SmsRules.MAX_CONTENT_LEN + 6)));
    }

    @Test
    void shouldAllowContentExactlyAtSignedLimit() {
        String content = "a".repeat(SmsRules.MAX_CONTENT_LEN - 6);

        SmsRules.SmsDraft draft = SmsRules.validateAndNormalize(MOBILES, content, "Acme");

        assertEquals(SmsRules.MAX_CONTENT_LEN, draft.content().length());
        assertTrue(draft.content().startsWith("【Acme】"));
    }

    @Test
    void shouldCountSignatureLengthIncludingBrackets() {
        String longSignature = "签".repeat(SmsRules.MAX_SIGNATURE_LEN + 10);

        String signed = SmsRules.appendSignature("正文", longSignature);

        assertEquals(SmsRules.MAX_SIGNATURE_LEN + 2, signed.length());
        assertTrue(signed.startsWith("【"));
        assertTrue(signed.substring(0, SmsRules.MAX_SIGNATURE_LEN).endsWith("】"));
    }

    @Test
    void shouldTruncateWrappedSignatureWithoutLosingBrackets() {
        String wrapped = "【" + "签".repeat(SmsRules.MAX_SIGNATURE_LEN + 10) + "】";

        String signed = SmsRules.appendSignature("正文", wrapped);

        assertEquals(SmsRules.MAX_SIGNATURE_LEN + 2, signed.length());
        assertTrue(signed.startsWith("【"));
        assertTrue(signed.substring(0, SmsRules.MAX_SIGNATURE_LEN).endsWith("】"));
    }

    @Test
    void shouldUseSanitizedSignatureLengthForBudget() {
        String content = "a".repeat(SmsRules.MAX_CONTENT_LEN - 6);

        SmsRules.SmsDraft draft = SmsRules.validateAndNormalize(
                MOBILES, content, "【Ac\nme\r\t】");

        assertEquals(SmsRules.MAX_CONTENT_LEN, draft.content().length());
        assertTrue(draft.content().startsWith("【Acme】"));
    }

    @Test
    void shouldStillEnforceLimitWithoutSignature() {
        String atLimit = "a".repeat(SmsRules.MAX_CONTENT_LEN);
        assertEquals(atLimit,
                SmsRules.validateAndNormalize(MOBILES, atLimit, null).content());

        String overLimit = "a".repeat(SmsRules.MAX_CONTENT_LEN + 1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SmsRules.validateAndNormalize(MOBILES, overLimit, "  "));
        assertTrue(ex.getMessage().contains("短信内容超过长度上限"));
    }

    @Test
    void shouldKeepMobileNormalizationUnchanged() {
        SmsRules.SmsDraft draft = SmsRules.validateAndNormalize(
                List.of("+86 138-0013-8000", "13800138000"), "正文", null);

        assertEquals(List.of("13800138000"), draft.mobiles());
        assertEquals("正文", draft.content());
    }
}
