package com.acme.scaffold.monitor.alert.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.rules.AlertInboxRules.Summary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 告警收件箱查询规则单测：锁定容错筛选 posture、读取上限钳制与汇总统计不变量。
 */
class AlertInboxRulesTest {

    @Test
    void normalizeStatusShouldAcceptWhitelistedValuesCaseInsensitively() {
        assertEquals("firing", AlertInboxRules.normalizeStatus(" FIRING "));
        assertEquals("resolved", AlertInboxRules.normalizeStatus("Resolved"));
        assertNull(AlertInboxRules.normalizeStatus("pending"), "白名单外状态一律忽略，不得静默映射");
        assertNull(AlertInboxRules.normalizeStatus(null));
        assertNull(AlertInboxRules.normalizeStatus("  "));
    }

    @Test
    void normalizeSeverityShouldTrimLowercaseAndTruncate() {
        assertEquals("critical", AlertInboxRules.normalizeSeverity("  CRITICAL "));
        assertEquals("a".repeat(AlertInboxRules.MAX_SEVERITY_LEN),
                AlertInboxRules.normalizeSeverity("A".repeat(50)));
        assertNull(AlertInboxRules.normalizeSeverity(null));
        assertNull(AlertInboxRules.normalizeSeverity(""));
    }

    @Test
    void normalizeLimitShouldApplyDefaultAndClampBounds() {
        assertEquals(100, AlertInboxRules.normalizeLimit(null, 200));
        assertEquals(50, AlertInboxRules.normalizeLimit(null, 50), "默认值不得超过容量");
        assertEquals(1, AlertInboxRules.normalizeLimit(0, 200));
        assertEquals(1, AlertInboxRules.normalizeLimit(-3, 200));
        assertEquals(200, AlertInboxRules.normalizeLimit(999, 200));
        assertEquals(1, AlertInboxRules.normalizeLimit(10, 0), "容量非法时下限至少为 1");
    }

    @Test
    void filterShouldPreserveOrderWhenNoEffectiveCondition() {
        List<AlertInboxEntry> entries = List.of(entry("firing", "critical", 1), entry("resolved", "info", 2));
        List<AlertInboxEntry> result = AlertInboxRules.filter(entries, "pending", "  ");
        assertEquals(2, result.size());
        assertEquals(entries, result);
        assertTrue(AlertInboxRules.filter(null, null, null).isEmpty());
        assertTrue(AlertInboxRules.filter(List.of(), "firing", null).isEmpty());
    }

    @Test
    void filterShouldMatchStatusAndSeverityCaseInsensitively() {
        List<AlertInboxEntry> entries = List.of(
                entry("FIRING", "Critical", 1),
                entry("firing", "warning", 2),
                entry("resolved", "critical", 3));
        List<AlertInboxEntry> result = AlertInboxRules.filter(entries, " firing ", " CRITICAL ");
        assertEquals(List.of(entries.get(0)), result, "两个条件同时生效且大小写不敏感");
    }

    @Test
    void filterShouldIgnoreInvalidStatusButKeepValidSeverity() {
        List<AlertInboxEntry> entries = List.of(
                entry("firing", "critical", 1), entry("resolved", "critical", 2));
        List<AlertInboxEntry> result = AlertInboxRules.filter(entries, "pending", "critical");
        assertEquals(2, result.size(), "非法 status 等同不筛选，severity 仍需生效");
    }

    @Test
    void summarizeShouldCountStatusesAndBucketSeveritiesDeterministically() {
        List<AlertInboxEntry> entries = List.of(
                entry("firing", "critical", 100),
                entry("FIRING", "warning", 300),
                entry("resolved", "critical", 200),
                entry("resolved", null, 400),
                entry("resolved", "  ", 500),
                entry("pending", "info", 600));
        Summary s = AlertInboxRules.summarize(entries, 200);

        assertEquals(200, s.capacity());
        assertEquals(6, s.total());
        assertEquals(2, s.firing(), "pending 不在 firing/resolved 白名单内，不计入两者");
        assertEquals(3, s.resolved());
        assertEquals(List.of("critical", "unknown", "info", "warning"),
                List.copyOf(s.severityCounts().keySet()), "计数降序、同计数按字母序");
        assertEquals(2, s.severityCounts().get("unknown"), "空 severity 归入 unknown 桶");
        assertEquals(s.total(), s.severityCounts().values().stream().mapToInt(Integer::intValue).sum(),
                "总数必须等于各 severity 桶之和");
    }

    @Test
    void summarizeShouldReportRetentionWindowInEpochMillis() {
        List<AlertInboxEntry> entries = List.of(
                entry("firing", "critical", 300),
                entry("resolved", "info", 100),
                new AlertInboxEntry("x", null, "firing", "n", "info", "s", "i", null, null));
        Summary s = AlertInboxRules.summarize(entries, 200);
        assertEquals(300L, s.mostRecentEpochMillis());
        assertEquals(100L, s.oldestRetainedEpochMillis());
    }

    @Test
    void summarizeShouldReturnZerosForEmptyInbox() {
        Summary s = AlertInboxRules.summarize(null, 200);
        assertEquals(0, s.total());
        assertEquals(0, s.firing());
        assertEquals(0, s.resolved());
        assertEquals(Map.of(), s.severityCounts());
        assertEquals(0L, s.mostRecentEpochMillis());
        assertEquals(0L, s.oldestRetainedEpochMillis());
    }

    private static AlertInboxEntry entry(String status, String severity, long receivedAtMillis) {
        return new AlertInboxEntry(
                "id-" + receivedAtMillis,
                Instant.ofEpochMilli(receivedAtMillis),
                status,
                "alert",
                severity,
                "summary",
                "instance",
                null,
                null);
    }
}
