package com.acme.scaffold.system.tenant.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.system.notifylog.domain.rule.NotificationRecordRules;
import com.acme.scaffold.system.smslog.domain.rule.SmsRecordRules;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 日志状态与租户编码 Locale 隔离：SKIPPED / DIGEST_PENDING / 租户编码不受 tr-TR 影响。 */
class LogAndTenantLocaleTest {

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
    void logStatusesShouldNormalizeUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals("SKIPPED", NotificationRecordRules.normalizeStatus("skipped"));
        assertEquals("DIGEST_PENDING", NotificationRecordRules.normalizeStatus("digest_pending"));
        assertEquals("SUCCESS", SmsRecordRules.normalizeStatus("success"));
    }

    @Test
    void tenantCodesShouldDeriveWithRootLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals("TENANT_ADMIN_INVY", TenantProvisioningRules.deriveRoleCode("invy"));
        assertEquals("DEPT_INVY", TenantProvisioningRules.deriveDeptCode("invy"));
    }
}
