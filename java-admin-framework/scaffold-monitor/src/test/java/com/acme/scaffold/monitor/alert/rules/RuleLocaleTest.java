package com.acme.scaffold.monitor.alert.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 告警规则 Locale 隔离：枚举解析与筛选归一不得受 JVM 默认 Locale 影响。 */
class RuleLocaleTest {

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
    void alertEnumsShouldParseUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals(AlertRuleRules.Severity.INFO, AlertRuleRules.parseSeverity("info"));
        assertEquals(AlertRuleRules.Severity.CRITICAL, AlertRuleRules.parseSeverity("critical"));
        assertEquals(AlertRuleRules.MetricKey.DISK_USAGE, AlertRuleRules.parseMetricKey("disk_usage"));
        assertEquals(AlertRuleRules.MetricKey.ONLINE_USERS, AlertRuleRules.parseMetricKey("online_users"));
        assertEquals(AlertRuleRules.ChannelType.INBOX, AlertRuleRules.parseChannelType("inbox"));
    }

    @Test
    void alertInboxNormalizationShouldWorkUnderTurkishLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        assertEquals(AlertInboxRules.STATUS_FIRING, AlertInboxRules.normalizeStatus("FIRING"));
        assertEquals("info", AlertInboxRules.normalizeSeverity("INFO"));
        assertEquals("critical", AlertInboxRules.normalizeSeverity("CRITICAL"));
    }
}
