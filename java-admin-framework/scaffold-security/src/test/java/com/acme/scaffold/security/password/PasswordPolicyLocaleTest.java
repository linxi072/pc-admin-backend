package com.acme.scaffold.security.password;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.acme.scaffold.common.exception.BusinessException;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 弱口令匹配 Locale 隔离：Admin123! 在 tr-TR 下仍须按 admin123 命中。 */
class PasswordPolicyLocaleTest {

    private Locale originalLocale;

    @BeforeEach
    void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void commonWeakPasswordShouldBeRejectedUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        // ADMIN123 → ROOT "admin123" 命中弱口令；旧口径在 tr-TR 下变成 "admın123" 从而绕过。
        PasswordPolicy.PolicyConfig config = new PasswordPolicy.PolicyConfig(8, 64, true, false, true, false, true);
        assertThrows(BusinessException.class, () -> PasswordPolicy.validate("ADMIN123", config));
    }
}
