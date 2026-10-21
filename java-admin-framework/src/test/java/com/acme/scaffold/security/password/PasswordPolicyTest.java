package com.acme.scaffold.security.password;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    @Test
    void validPassword_passes() {
        // 满足：长度>=8、含大/小/数字/特殊字符、非弱口令
        PasswordPolicy.validate("Abcd123!@#");
    }

    @Test
    void tooShort_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PasswordPolicy.validate("Ab1!"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR.code(), ex.getErrorCode().code());
    }

    @Test
    void missingSpecialChar_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PasswordPolicy.validate("Abcd1234"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR.code(), ex.getErrorCode().code());
    }

    @Test
    void missingUpper_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PasswordPolicy.validate("abcd123!@#"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR.code(), ex.getErrorCode().code());
    }

    @Test
    void commonWeakPassword_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PasswordPolicy.validate("admin123"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR.code(), ex.getErrorCode().code());
    }

    @Test
    void customConfig_relaxesRules() {
        // 自定义策略：仅要求长度与数字，无需特殊字符
        PasswordPolicy.PolicyConfig relaxed = new PasswordPolicy.PolicyConfig(
                6, 64, false, false, true, false, false);
        PasswordPolicy.validate("abcd12", relaxed);
    }

    @Test
    void customConfig_obeysLengthBound() {
        PasswordPolicy.PolicyConfig relaxed = new PasswordPolicy.PolicyConfig(
                6, 64, false, false, true, false, false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> PasswordPolicy.validate("ab1", relaxed));
        assertEquals(CommonErrorCode.VALIDATION_ERROR.code(), ex.getErrorCode().code());
    }
}
