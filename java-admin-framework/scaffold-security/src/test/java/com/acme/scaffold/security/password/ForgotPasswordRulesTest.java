package com.acme.scaffold.security.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ForgotPasswordRules} 单元测试：身份核验判定的纯函数行为。
 *
 * <p>覆盖三类分支：凭据充分性（{@link ForgotPasswordRules#hasVerifier}）、
 * 单值匹配（{@link ForgotPasswordRules#matches}）、总判定
 * （{@link ForgotPasswordRules#verified}），含 null/空白/库中字段为空等边界。
 */
class ForgotPasswordRulesTest {

    // ---------- hasVerifier：至少提供一种验证凭据 ----------

    @Test
    @DisplayName("手机号或邮箱任一非空即视为提供了验证凭据")
    void hasVerifier_eitherProvided() {
        assertTrue(ForgotPasswordRules.hasVerifier("13800000001", null));
        assertTrue(ForgotPasswordRules.hasVerifier(null, "a@x.com"));
        assertTrue(ForgotPasswordRules.hasVerifier("13800000001", "a@x.com"));
    }

    @Test
    @DisplayName("两者均为 null 或空白视为未提供验证凭据")
    void hasVerifier_noneProvided() {
        assertFalse(ForgotPasswordRules.hasVerifier(null, null));
        assertFalse(ForgotPasswordRules.hasVerifier("", ""));
        assertFalse(ForgotPasswordRules.hasVerifier("   ", "\t\n"));
    }

    // ---------- matches：单值 null 安全匹配 ----------

    @Test
    @DisplayName("两端非空白且去首尾空白后相等才匹配")
    void matches_exactAfterTrim() {
        assertTrue(ForgotPasswordRules.matches("13800000001", "13800000001"));
        assertTrue(ForgotPasswordRules.matches(" 13800000001 ", "13800000001"));
        assertFalse(ForgotPasswordRules.matches("13800000001", "13800000002"));
    }

    @Test
    @DisplayName("库中值为 null 或空白时永不匹配（防空串撞空库）")
    void matches_nullOrBlankStored() {
        assertFalse(ForgotPasswordRules.matches(null, "13800000001"));
        assertFalse(ForgotPasswordRules.matches("", "13800000001"));
        assertFalse(ForgotPasswordRules.matches("   ", "13800000001"));
        // 输入侧空白同样不匹配
        assertFalse(ForgotPasswordRules.matches("13800000001", "   "));
        assertFalse(ForgotPasswordRules.matches("13800000001", null));
    }

    // ---------- verified：总判定（任一匹配即通过） ----------

    @Test
    @DisplayName("手机号匹配即通过（邮箱不参与）")
    void verified_mobileMatch() {
        assertTrue(ForgotPasswordRules.verified("13800000001", "a@x.com", "13800000001", null));
    }

    @Test
    @DisplayName("邮箱匹配即通过（手机号不参与）")
    void verified_emailMatch() {
        assertTrue(ForgotPasswordRules.verified("13800000001", "a@x.com", null, "a@x.com"));
    }

    @Test
    @DisplayName("手机号与邮箱均不匹配则不通过")
    void verified_neitherMatch() {
        assertFalse(ForgotPasswordRules.verified("13800000001", "a@x.com", "13800000002", "b@x.com"));
    }

    @Test
    @DisplayName("库中手机号邮箱均为空时不通过（账号无登记信息不可自助找回）")
    void verified_bothStoredEmpty() {
        assertFalse(ForgotPasswordRules.verified(null, null, "13800000001", "a@x.com"));
        assertFalse(ForgotPasswordRules.verified("", "", "13800000001", "a@x.com"));
    }
}
