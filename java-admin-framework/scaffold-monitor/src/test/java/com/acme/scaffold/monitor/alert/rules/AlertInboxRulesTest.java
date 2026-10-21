package com.acme.scaffold.monitor.alert.rules;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警收件箱规则类离线单测：筛选归一、limit 钳制、纯筛选与汇总口径。
 */
class AlertInboxRulesTest {

    private static AlertInboxEntry entry(String id, String status, String severity, Instant receivedAt) {
        return new AlertInboxEntry(id, receivedAt, status, "HighErrorRate", severity,
                "error rate > 1%", "10.0.0.1:8080", "2026-10-20T09:00:00Z", null);
    }

    // ---------- normalizeStatus ----------

    @Test
    @DisplayName("状态归一：白名单值大小写不敏感放行，其余一律 null（忽略而非报错）")
    void normalizeStatus_whitelist() {
        assertEquals("firing", AlertInboxRules.normalizeStatus("FIRING"));
        assertEquals("resolved", AlertInboxRules.normalizeStatus(" Resolved "));
        assertNull(AlertInboxRules.normalizeStatus("pending"));
        assertNull(AlertInboxRules.normalizeStatus(""));
        assertNull(AlertInboxRules.normalizeStatus("   "));
        assertNull(AlertInboxRules.normalizeStatus(null));
    }

    // ---------- normalizeSeverity ----------

    @Test
    @DisplayName("severity 归一：trim+小写+截断；空值 null；自由 label 不做白名单")
    void normalizeSeverity_freeForm() {
        assertEquals("critical", AlertInboxRules.normalizeSeverity(" Critical "));
        assertEquals("p1-oncall", AlertInboxRules.normalizeSeverity("P1-OnCall"));
        // 空 severity 返回 null（忽略筛选）；unknown 桶是 summarize 对「条目缺 severity」的归类，
        // 不是归一化产出——两者语义不同，不可混用
        assertNull(AlertInboxRules.normalizeSeverity("  "));
        assertNull(AlertInboxRules.normalizeSeverity(null));
        String long_ = "s".repeat(AlertInboxRules.MAX_SEVERITY_LEN + 10);
        assertEquals(AlertInboxRules.MAX_SEVERITY_LEN,
                AlertInboxRules.normalizeSeverity(long_).length());
    }

    // ---------- normalizeLimit ----------

    @Test
    @DisplayName("limit 钳制：null 取默认 100；<1 钳 1；超容量按容量自适应；maxAllowed<=0 时保底 1")
    void normalizeLimit_clamps() {
        assertEquals(100, AlertInboxRules.normalizeLimit(null, 200));
        assertEquals(50, AlertInboxRules.normalizeLimit(null, 50)); // 默认值同样受容量约束
        assertEquals(1, AlertInboxRules.normalizeLimit(0, 200));
        assertEquals(1, AlertInboxRules.normalizeLimit(-7, 200));
        assertEquals(200, AlertInboxRules.normalizeLimit(9999, 200));
        assertEquals(1, AlertInboxRules.normalizeLimit(null, 0)); // 容量误填 0 不放大成 0 条
        assertEquals(1, AlertInboxRules.normalizeLimit(5, -3));
    }

    // ---------- filter ----------

    @Test
    @DisplayName("纯筛选：按 status/severity 大小写不敏感精确匹配，保持入参顺序，空条件原样放行")
    void filter_matchesAndKeepsOrder() {
        Instant t = Instant.parse("2026-10-20T10:00:00Z");
        List<AlertInboxEntry> entries = List.of(
                entry("a", "firing", "critical", t),
                entry("b", "firing", "warning", t),
                entry("c", "resolved", "critical", t),
                entry("d", "firing", "critical", t.plusSeconds(60)));

        assertEquals(4, AlertInboxRules.filter(entries, null, null).size());
        assertEquals(List.of("a", "d"), ids(AlertInboxRules.filter(entries, "FIRING", "critical")));
        assertEquals(List.of("b"), ids(AlertInboxRules.filter(entries, "firing", "warning")));
        assertEquals(List.of("c"), ids(AlertInboxRules.filter(entries, "resolved", null)));
        // 白名单外 status：容错忽略（归一为 null = 不附加该条件），等同不筛选而非返回空
        assertEquals(List.of("a", "b", "c", "d"), ids(AlertInboxRules.filter(entries, "pending", null)));
    }

    @Test
    @DisplayName("纯筛选：null/空入参返回空列表（不抛 NPE）")
    void filter_nullSafe() {
        assertTrue(AlertInboxRules.filter(null, "firing", null).isEmpty());
        assertTrue(AlertInboxRules.filter(List.of(), null, null).isEmpty());
    }

    // ---------- summarize ----------

    @Test
    @DisplayName("汇总：状态分布、severity 计数降序（同数按键名序）、unknown 桶守恒、时间戳口径")
    void summarize_countsAndOrder() {
        Instant base = Instant.parse("2026-10-20T10:00:00Z");
        List<AlertInboxEntry> entries = List.of(
                entry("a", "firing", "critical", base),
                entry("b", "firing", "critical", base.plusSeconds(30)),
                entry("c", "firing", "warning", base.plusSeconds(10)),
                entry("d", "resolved", null, base.minusSeconds(60)));

        AlertInboxRules.Summary s = AlertInboxRules.summarize(entries, 200);

        assertEquals(200, s.capacity());
        assertEquals(4, s.total());
        assertEquals(3, s.firing());
        assertEquals(1, s.resolved());
        // critical=2 > warning=1 = unknown=1；同数按字母序 unknown < warning
        assertEquals(List.of("critical", "unknown", "warning"), List.copyOf(s.severityCounts().keySet()));
        assertEquals(2, s.severityCounts().get("critical"));
        assertEquals(1, s.severityCounts().get("unknown"));
        // 总数 = 各桶之和（unknown 桶存在的意义）
        assertEquals(s.total(), s.severityCounts().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(base.plusSeconds(30).toEpochMilli(), s.mostRecentEpochMillis());
        assertEquals(base.minusSeconds(60).toEpochMilli(), s.oldestRetainedEpochMillis());
    }

    @Test
    @DisplayName("汇总：空箱全零、时间戳 0、severity 分布空 Map")
    void summarize_empty() {
        AlertInboxRules.Summary s = AlertInboxRules.summarize(List.of(), 200);
        assertEquals(0, s.total());
        assertEquals(0, s.firing());
        assertEquals(0, s.resolved());
        assertEquals(0L, s.mostRecentEpochMillis());
        assertEquals(0L, s.oldestRetainedEpochMillis());
        assertNotNull(s.severityCounts());
        assertTrue(s.severityCounts().isEmpty());
    }

    @Test
    @DisplayName("汇总：null 入参不抛 NPE（视为空箱）")
    void summarize_nullSafe() {
        AlertInboxRules.Summary s = AlertInboxRules.summarize(null, 50);
        assertEquals(0, s.total());
        assertEquals(50, s.capacity());
    }

    @Test
    @DisplayName("汇总：receivedAt 为 null 的条目不参与时间戳统计但不丢计数")
    void summarize_nullReceivedAt() {
        List<AlertInboxEntry> entries = List.of(entry("a", "firing", "critical", null));
        AlertInboxRules.Summary s = AlertInboxRules.summarize(entries, 10);
        assertEquals(1, s.total());
        assertEquals(0L, s.mostRecentEpochMillis());
        assertEquals(0L, s.oldestRetainedEpochMillis());
    }

    // ---------- truncate ----------

    @Test
    @DisplayName("截断：null 原样、短文本原样、超长取前 max 字符、max<=0 返回空串")
    void truncate_behaviour() {
        assertNull(AlertInboxRules.truncate(null, 10));
        assertEquals("abc", AlertInboxRules.truncate("abc", 10));
        assertEquals("abcd", AlertInboxRules.truncate("abcdef", 4));
        assertEquals("", AlertInboxRules.truncate("abc", 0));
        assertEquals("", AlertInboxRules.truncate("abc", -1));
        assertSame(AlertInboxRules.truncate("short", 100), "short");
    }

    // ---------- 汇总结果不可变性守护 ----------

    @Test
    @DisplayName("汇总结果：severityCounts 为保序 Map 且不含 null 键（JSON 序列化稳定）")
    void summarize_mapIsOrderedAndClean() {
        AlertInboxRules.Summary s = AlertInboxRules.summarize(
                List.of(entry("a", "firing", "Critical", Instant.now())), 10);
        Map<String, Integer> counts = s.severityCounts();
        // 输入大小写混写，桶键统一小写
        assertFalse(counts.containsKey("Critical"));
        assertEquals(1, counts.get("critical"));
    }

    private static List<String> ids(List<AlertInboxEntry> entries) {
        return entries.stream().map(AlertInboxEntry::getId).toList();
    }
}
