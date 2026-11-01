package com.acme.scaffold.monitor.alert.rules;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.entity.AlertRuleDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 告警规则纯函数离线单测：阈值比较、枚举解析、配置校验、合成告警构造。
 */
class AlertRuleRulesTest {

    private static AlertRuleDO rule() {
        AlertRuleDO d = new AlertRuleDO();
        d.setRuleName("CPU 高负载");
        d.setMetricKey("CPU_USAGE");
        d.setComparator("GT");
        d.setThreshold(new BigDecimal("80"));
        d.setSeverity("WARNING");
        d.setConsecutiveCount(1);
        d.setForDurationSec(0);
        d.setChannelType("INBOX");
        return d;
    }

    // ---------- compare ----------

    @Test
    @DisplayName("阈值比较：GT/GE/LT/LE/EQ/NE 各分支正确")
    void compare_allComparators() {
        BigDecimal v = new BigDecimal("85");
        BigDecimal t = new BigDecimal("80");
        assertTrue(AlertRuleRules.compare(v, AlertRuleRules.Comparator.GT, t));
        assertFalse(AlertRuleRules.compare(t, AlertRuleRules.Comparator.GT, t));
        assertTrue(AlertRuleRules.compare(t, AlertRuleRules.Comparator.GE, t));
        assertTrue(AlertRuleRules.compare(new BigDecimal("75"), AlertRuleRules.Comparator.LT, t));
        assertTrue(AlertRuleRules.compare(t, AlertRuleRules.Comparator.LE, t));
        assertTrue(AlertRuleRules.compare(t, AlertRuleRules.Comparator.EQ, t));
        assertTrue(AlertRuleRules.compare(new BigDecimal("90"), AlertRuleRules.Comparator.NE, t));
    }

    @Test
    @DisplayName("阈值比较：任一参数为 null 一律不触发（避免缺失指标误报）")
    void compare_nullSafe() {
        BigDecimal v = new BigDecimal("85");
        BigDecimal t = new BigDecimal("80");
        assertFalse(AlertRuleRules.compare(null, AlertRuleRules.Comparator.GT, t));
        assertFalse(AlertRuleRules.compare(v, null, t));
        assertFalse(AlertRuleRules.compare(v, AlertRuleRules.Comparator.GT, null));
    }

    // ---------- 解析 ----------

    @Test
    @DisplayName("枚举解析：合法值大小写不敏感，非法/空返回 null")
    void parse_enums() {
        assertEquals(AlertRuleRules.MetricKey.CPU_USAGE, AlertRuleRules.parseMetricKey("cpu_usage"));
        assertEquals(AlertRuleRules.Comparator.GT, AlertRuleRules.parseComparator(" gt "));
        assertEquals(AlertRuleRules.Severity.WARNING, AlertRuleRules.parseSeverity("warning"));
        assertEquals(AlertRuleRules.ChannelType.WEBHOOK, AlertRuleRules.parseChannelType("WEBHOOK"));
        assertNull(AlertRuleRules.parseMetricKey("nope"));
        assertNull(AlertRuleRules.parseComparator(""));
        assertNull(AlertRuleRules.parseSeverity(null));
        assertNull(AlertRuleRules.parseChannelType("  "));
    }

    // ---------- validate ----------

    @Test
    @DisplayName("配置校验：合法规则返回空错误列表")
    void validate_valid() {
        assertTrue(AlertRuleRules.validate(rule()).isEmpty());
    }

    @Test
    @DisplayName("配置校验：缺字段/非法枚举/负数窗口均报错")
    void validate_invalidFields() {
        AlertRuleDO d = rule();
        d.setRuleName("");
        d.setMetricKey("BAD");
        d.setComparator("BAD");
        d.setThreshold(null);
        d.setSeverity("BAD");
        d.setChannelType("BAD");
        d.setConsecutiveCount(0);
        d.setForDurationSec(-1);
        List<String> errs = AlertRuleRules.validate(d);
        assertEquals(8, errs.size());
    }

    @Test
    @DisplayName("配置校验：WEBHOOK 渠道缺少 channelConfig 报错")
    void validate_webhookRequiresConfig() {
        AlertRuleDO d = rule();
        d.setChannelType("WEBHOOK");
        d.setChannelConfig(null);
        List<String> errs = AlertRuleRules.validate(d);
        assertTrue(errs.stream().anyMatch(e -> e.contains("WEBHOOK")));
    }

    @Test
    @DisplayName("配置校验：null 规则对象返回单条错误")
    void validate_nullRule() {
        assertEquals(1, AlertRuleRules.validate(null).size());
    }

    // ---------- buildEntry ----------

    @Test
    @DisplayName("合成告警：status=firing、instance=rules-engine、summary 含阈值关系")
    void buildEntry_fields() {
        AlertRuleDO d = rule();
        AlertInboxEntry e = AlertRuleRules.buildEntry(d, new BigDecimal("92.5"), Instant.parse("2026-10-20T10:00:00Z"));
        assertEquals(AlertRuleRules.STATUS_FIRING, e.getStatus());
        assertEquals("rules-engine", e.getInstance());
        assertEquals("[规则] CPU 高负载", e.getName());
        assertEquals("WARNING", e.getSeverity());
        assertTrue(e.getSummary().contains("92.5"));
        assertTrue(e.getSummary().contains("80"));
        assertTrue(e.getSummary().contains(">"));
        assertNotNull(e.getId());
    }

    @Test
    @DisplayName("合成告警：缺省严重度回退 WARNING、缺省名称回退未命名")
    void buildEntry_defaults() {
        AlertRuleDO d = new AlertRuleDO();
        d.setMetricKey("CPU_USAGE");
        d.setComparator("GT");
        d.setThreshold(new BigDecimal("80"));
        AlertInboxEntry e = AlertRuleRules.buildEntry(d, new BigDecimal("90"), Instant.now());
        assertEquals("WARNING", e.getSeverity());
        assertEquals("[规则] 未命名规则", e.getName());
    }
}
