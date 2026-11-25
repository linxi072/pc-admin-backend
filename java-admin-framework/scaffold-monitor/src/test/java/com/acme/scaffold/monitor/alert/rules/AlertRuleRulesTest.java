package com.acme.scaffold.monitor.alert.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules.ChannelType;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules.Comparator;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules.MetricKey;
import com.acme.scaffold.monitor.alert.rules.AlertRuleRules.Severity;
import com.acme.scaffold.monitor.entity.AlertRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 告警规则纯函数单测：锁定枚举容错解析、六种阈值比较、配置校验与合成告警构造契约。
 */
class AlertRuleRulesTest {

    @Test
    void enumParsersShouldBeCaseInsensitiveAndTolerant() {
        assertEquals(MetricKey.CPU_USAGE, AlertRuleRules.parseMetricKey(" cpu_usage "));
        assertEquals(Comparator.GE, AlertRuleRules.parseComparator("ge"));
        assertEquals(Severity.WARNING, AlertRuleRules.parseSeverity("Warning"));
        assertEquals(ChannelType.WEBHOOK, AlertRuleRules.parseChannelType(" webhook "));

        assertNull(AlertRuleRules.parseMetricKey("unknown"));
        assertNull(AlertRuleRules.parseComparator(null));
        assertNull(AlertRuleRules.parseSeverity("  "));
        assertNull(AlertRuleRules.parseChannelType("INBOX_OR_WEBHOOK"));
    }

    @Test
    void compareShouldHonorAllSixComparators() {
        BigDecimal actual = new BigDecimal("85");
        BigDecimal threshold = new BigDecimal("80");
        assertTrue(AlertRuleRules.compare(actual, Comparator.GT, threshold));
        assertTrue(AlertRuleRules.compare(actual, Comparator.GE, threshold));
        assertFalse(AlertRuleRules.compare(actual, Comparator.LT, threshold));
        assertFalse(AlertRuleRules.compare(actual, Comparator.LE, threshold));
        assertFalse(AlertRuleRules.compare(actual, Comparator.EQ, threshold));
        assertTrue(AlertRuleRules.compare(actual, Comparator.NE, threshold));
    }

    @Test
    void compareShouldUseBoundarySemantics() {
        BigDecimal same = new BigDecimal("80");
        BigDecimal threshold = new BigDecimal("80");
        assertFalse(AlertRuleRules.compare(same, Comparator.GT, threshold));
        assertTrue(AlertRuleRules.compare(same, Comparator.GE, threshold));
        assertTrue(AlertRuleRules.compare(same, Comparator.LE, threshold));
        assertTrue(AlertRuleRules.compare(same, Comparator.EQ, threshold));
        assertFalse(AlertRuleRules.compare(same, Comparator.NE, threshold));
    }

    @Test
    void compareShouldNotFireOnMissingInputs() {
        BigDecimal one = BigDecimal.ONE;
        assertFalse(AlertRuleRules.compare(null, Comparator.GT, one));
        assertFalse(AlertRuleRules.compare(one, null, one));
        assertFalse(AlertRuleRules.compare(one, Comparator.GT, null));
    }

    @Test
    void validateShouldAcceptFullyValidRule() {
        assertTrue(AlertRuleRules.validate(validRule()).isEmpty());
    }

    @Test
    void validateShouldRejectNullRule() {
        List<String> errors = AlertRuleRules.validate(null);
        assertEquals(List.of("规则对象不能为空"), errors);
    }

    @Test
    void validateShouldCollectAllInvalidFieldErrors() {
        AlertRule rule = new AlertRule();
        List<String> errors = AlertRuleRules.validate(rule);
        assertEquals(6, errors.size(), "空对象应一次性报出全部空字段错误（计次/时长有合法默认值）");
        assertTrue(errors.contains("指标类型(metricKey)非法"));
        assertTrue(errors.contains("渠道类型(channelType)非法"));
    }

    @Test
    void validateShouldRequireChannelConfigOnlyForWebhook() {
        AlertRule inbox = validRule();
        inbox.setChannelType("INBOX");
        inbox.setChannelConfig(null);
        assertTrue(AlertRuleRules.validate(inbox).isEmpty());

        AlertRule webhook = validRule();
        webhook.setChannelType("WEBHOOK");
        webhook.setChannelConfig("  ");
        assertEquals(List.of("WEBHOOK 渠道必须配置 channelConfig"), AlertRuleRules.validate(webhook));
    }

    @Test
    void validateShouldRejectInvalidConsecutiveCountAndNegativeDuration() {
        AlertRule rule = validRule();
        rule.setConsecutiveCount(0);
        rule.setForDurationSec(-1);
        List<String> errors = AlertRuleRules.validate(rule);
        assertTrue(errors.contains("连续触发次数(consecutiveCount)必须 >= 1"));
        assertTrue(errors.contains("持续时长(forDurationSec)不能为负"));
    }

    @Test
    void buildEntryShouldProduceFiringSyntheticAlertWithRuleContext() {
        AlertRule rule = validRule();
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        AlertInboxEntry entry = AlertRuleRules.buildEntry(rule, new BigDecimal("85"), now);

        assertNotNull(entry.getId());
        assertEquals(now, entry.getReceivedAt());
        assertEquals(AlertRuleRules.STATUS_FIRING, entry.getStatus());
        assertEquals("rules-engine", entry.getInstance());
        assertEquals("WARNING", entry.getSeverity());
        assertEquals("[规则] CPU 高", entry.getName());
        assertTrue(entry.getSummary().contains("85"), "摘要应含当前采样值");
        assertTrue(entry.getSummary().contains("80"), "摘要应含阈值");
        assertTrue(entry.getSummary().contains(">"), "摘要应含比较符");
    }

    @Test
    void buildEntryShouldFallbackForBlankConfiguration() {
        AlertRule rule = new AlertRule();
        rule.setComparator("GT");
        AlertInboxEntry entry = AlertRuleRules.buildEntry(rule, BigDecimal.TEN, Instant.EPOCH);
        assertEquals("[规则] 未命名规则", entry.getName());
        assertEquals(Severity.WARNING.name(), entry.getSeverity());
    }

    private static AlertRule validRule() {
        AlertRule rule = new AlertRule();
        rule.setRuleName("CPU 高");
        rule.setMetricKey("CPU_USAGE");
        rule.setComparator("GT");
        rule.setThreshold(new BigDecimal("80"));
        rule.setSeverity("WARNING");
        rule.setChannelType("INBOX");
        rule.setConsecutiveCount(1);
        rule.setForDurationSec(0);
        return rule;
    }
}
