package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EmailRules} 单元测试：地址校验矩阵、CRLF 净化、收件人归一与上限、
 * 报文草稿组装。纯函数离线验证，不触达 SMTP。
 */
class EmailRulesTest {

    @Test
    void validAddressesAccepted() {
        assertTrue(EmailRules.isValidAddress("ops@example.com"));
        assertTrue(EmailRules.isValidAddress("  ops@example.com  ")); // trim 后合法
        assertTrue(EmailRules.isValidAddress("first.last+tag@sub.example.co"));
        assertTrue(EmailRules.isValidAddress("a@b.cn"));
    }

    @Test
    void invalidAddressesRejected() {
        assertFalse(EmailRules.isValidAddress(null));
        assertFalse(EmailRules.isValidAddress(""));
        assertFalse(EmailRules.isValidAddress("   "));
        assertFalse(EmailRules.isValidAddress("no-at-sign"));
        assertFalse(EmailRules.isValidAddress("no-domain@dot"));
        assertFalse(EmailRules.isValidAddress("@example.com"));
        assertFalse(EmailRules.isValidAddress("two@@example.com"));
        assertFalse(EmailRules.isValidAddress("spaces in@example.com"));
        assertFalse(EmailRules.isValidAddress("header@inject\nBcc: evil@x.com"));
        assertFalse(EmailRules.isValidAddress("a@b.com".repeat(60))); // 超长（>254）
    }

    @Test
    void sanitizeHeaderStripsInjectionChars() {
        // CR/LF/TAB 被替换为空格：注入的 "Bcc: evil@x.com" 退化为 inert 文本，无法构成新邮件头
        assertEquals("系统通知 Bcc: evil@x.com 请查收",
                EmailRules.sanitizeHeader("系统通知\r\nBcc: evil@x.com\t请查收", 200));
        // 超长截断到上限
        assertEquals(10, EmailRules.sanitizeHeader("一二三四五六七八九十十一", 10).length());
        assertEquals("", EmailRules.sanitizeHeader(null, 100));
    }

    @Test
    void normalizeRecipientsTrimsDedupesAndRejectsEmpty() {
        List<String> normalized = EmailRules.normalizeRecipients(
                List.of(" a@x.com ", "b@x.com", "a@x.com", "", "   ", "c@x.com"));
        assertEquals(List.of("a@x.com", "b@x.com", "c@x.com"), normalized);

        assertThrows(BusinessException.class, () -> EmailRules.normalizeRecipients(null));
        assertThrows(BusinessException.class, () -> EmailRules.normalizeRecipients(List.of()));
        assertThrows(BusinessException.class, () -> EmailRules.normalizeRecipients(List.of("  ")));
    }

    @Test
    void normalizeRecipientsRejectsOverLimit() {
        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i <= EmailRules.MAX_RECIPIENTS; i++) {
            tooMany.add("user" + i + "@x.com");
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> EmailRules.normalizeRecipients(tooMany));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains(String.valueOf(EmailRules.MAX_RECIPIENTS)));
    }

    @Test
    void validateAndNormalizeBuildsCleanDraft() {
        EmailRules.MailDraft draft = EmailRules.validateAndNormalize(
                "noreply@example.com",
                List.of(" ops@example.com ", "ops@example.com", "dev@example.com"),
                "  告警通知\r\nBcc: evil@x.com  ",
                "CPU 使用率超过阈值");

        assertEquals("noreply@example.com", draft.from());
        assertEquals(List.of("ops@example.com", "dev@example.com"), draft.to());
        // 主题经 CRLF 净化（替换为空格）且 trim
        assertEquals("告警通知 Bcc: evil@x.com", draft.subject());
        assertEquals("CPU 使用率超过阈值", draft.content());
    }

    @Test
    void validateRejectsMissingFrom() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> EmailRules.validateAndNormalize(null, List.of("a@x.com"), "s", "c"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("发件人"));

        ex = assertThrows(BusinessException.class,
                () -> EmailRules.validateAndNormalize("  ", List.of("a@x.com"), "s", "c"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void validateRejectsIllegalFromAddress() {
        assertThrows(BusinessException.class,
                () -> EmailRules.validateAndNormalize("not-an-email", List.of("a@x.com"), "s", "c"));
    }

    @Test
    void validateRejectsIllegalRecipient() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> EmailRules.validateAndNormalize("from@x.com",
                        List.of("ok@x.com", "bad-address"), "s", "c"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("收件人地址非法"));
    }

    @Test
    void validateRejectsBlankSubjectOrContent() {
        assertThrows(BusinessException.class, () -> EmailRules.validateAndNormalize(
                "from@x.com", List.of("a@x.com"), "   ", "c"));
        assertThrows(BusinessException.class, () -> EmailRules.validateAndNormalize(
                "from@x.com", List.of("a@x.com"), "s", null));
        assertThrows(BusinessException.class, () -> EmailRules.validateAndNormalize(
                "from@x.com", List.of("a@x.com"), "s", "  "));
    }

    @Test
    void validateRejectsOverlongContent() {
        String huge = "x".repeat(EmailRules.MAX_CONTENT_LEN + 1);
        BusinessException ex = assertThrows(BusinessException.class, () -> EmailRules.validateAndNormalize(
                "from@x.com", List.of("a@x.com"), "s", huge));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("长度上限"));
    }

    @Test
    void subjectTruncatedToMaxLen() {
        String longSubject = "标".repeat(EmailRules.MAX_SUBJECT_LEN + 50);
        EmailRules.MailDraft draft = EmailRules.validateAndNormalize(
                "from@x.com", List.of("a@x.com"), longSubject, "c");
        assertEquals(EmailRules.MAX_SUBJECT_LEN, draft.subject().length());
    }
}
