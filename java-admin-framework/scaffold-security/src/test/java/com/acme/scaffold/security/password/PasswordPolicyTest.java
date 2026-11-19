package com.acme.scaffold.security.password;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码策略单测（V1.1.0 测试覆盖重建立项：scaffold-security 批次）。
 * 覆盖默认策略与自定义策略的全部分支。
 */
class PasswordPolicyTest {

    private static final String STRONG = "Str0ng!Pass";

    @Test
    void strongPasswordShouldPassDefaultPolicy() {
        assertDoesNotThrow(() -> PasswordPolicy.validate(STRONG));
    }

    @Test
    void blankOrNullShouldBeRejected() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(null));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(""));
    }

    @Test
    void tooShortShouldBeRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> PasswordPolicy.validate("Ab1!"));
        assertTrue(ex.getMessage().contains("长度"));
    }

    @Test
    void tooLongShouldBeRejected() {
        String longPwd = "Aa1!" + "x".repeat(61);
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(longPwd));
    }

    @Test
    void missingCharacterClassShouldBeRejected() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("lowercase1!"));     // 无大写
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("UPPERCASE1!"));     // 无小写
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("NoDigit!abc"));     // 无数字
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("NoSpecial123ab"));  // 无特殊字符
    }

    @Test
    void commonWeakPasswordShouldBeRejected() {
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("admin123"));
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("password123"));
        for (String weak : PasswordPolicy.commonWeakPasswords()) {
            assertTrue(weak.length() > 0);
        }
    }

    @Test
    void customPolicyShouldRelaxRules() {
        PasswordPolicy.PolicyConfig relaxed = new PasswordPolicy.PolicyConfig(4, 64, false, false, false, false, false);
        assertDoesNotThrow(() -> PasswordPolicy.validate("abcd", relaxed));
        assertDoesNotThrow(() -> PasswordPolicy.validate("12345678", relaxed));

        PasswordPolicy.PolicyConfig strictLen = new PasswordPolicy.PolicyConfig(12, 64, true, true, true, true, true);
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate(STRONG, strictLen));
    }

    @Test
    void weakDenyCanTurnOffByConfig() {
        PasswordPolicy.PolicyConfig noDeny = new PasswordPolicy.PolicyConfig(8, 64, true, true, true, true, false);
        assertDoesNotThrow(() -> PasswordPolicy.validate("password123", noDeny));
    }
}
