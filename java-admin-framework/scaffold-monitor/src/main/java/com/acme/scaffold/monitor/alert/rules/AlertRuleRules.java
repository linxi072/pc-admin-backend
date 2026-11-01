package com.acme.scaffold.monitor.alert.rules;

import com.acme.scaffold.monitor.alert.AlertInboxEntry;
import com.acme.scaffold.monitor.entity.AlertRuleDO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 告警规则纯函数规则：指标键 / 比较符 / 严重度 / 渠道枚举，阈值比较、配置校验、合成告警构造。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@code com.acme.scaffold.monitor.alert.service.AlertRuleService}（编排）复用，
 * 保证「怎么比」「怎么校验」「怎么合成告警」口径单一来源。与 {@link AlertInboxRules} 一致，
 * 全部为 {@code public static} 纯函数。</p>
 *
 * <p><b>告警状态语义</b>：规则触发产生的合成告警统一置为 {@value #STATUS_FIRING}，
 * instance 标 {@code rules-engine}，便于与 Alertmanager 推送的告警在收件箱中共存区分。</p>
 */
public final class AlertRuleRules {

    /** 合成告警状态：规则引擎产生的告警恒为 firing（无独立 resolved 状态，由收件箱留存即可）。 */
    public static final String STATUS_FIRING = "firing";

    private AlertRuleRules() {
    }

    // ---------------- 枚举 ----------------

    /** 可监控指标键：code 为采样器产出 Map 的 key，unit 为展示单位。 */
    public enum MetricKey {
        CPU_USAGE("cpuUsage", "%"),
        MEMORY_USAGE("memoryUsage", "%"),
        DISK_USAGE("diskUsage", "%"),
        THREAD_COUNT("threadCount", "个"),
        NPLUS1_COUNT("nPlus1Count", "个"),
        ONLINE_USERS("onlineUsers", "人"),
        ERROR_LOG_COUNT("errorLogCount", "条");

        private final String code;
        private final String unit;

        MetricKey(String code, String unit) {
            this.code = code;
            this.unit = unit;
        }

        public String code() {
            return code;
        }

        public String unit() {
            return unit;
        }
    }

    /** 阈值比较符。 */
    public enum Comparator {
        GT, GE, LT, LE, EQ, NE
    }

    /** 告警严重度（同时作为收件箱 severity label）。 */
    public enum Severity {
        CRITICAL, WARNING, INFO
    }

    /** 触发渠道类型。 */
    public enum ChannelType {
        INBOX, WEBHOOK
    }

    // ---------------- 解析（容错：非法/空返回 null） ----------------

    public static MetricKey parseMetricKey(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return MetricKey.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Comparator parseComparator(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return Comparator.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Severity parseSeverity(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return Severity.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ChannelType parseChannelType(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return ChannelType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------- 阈值比较（纯函数） ----------------

    /**
     * 比较 actual 与 threshold：按 comparator 关系判定是否超阈。
     * 任一参数为 null 返回 false（不触发），避免采样缺失或配置错误误报。
     */
    public static boolean compare(BigDecimal actual, Comparator comparator, BigDecimal threshold) {
        if (actual == null || comparator == null || threshold == null) {
            return false;
        }
        int c = actual.compareTo(threshold);
        return switch (comparator) {
            case GT -> c > 0;
            case GE -> c >= 0;
            case LT -> c < 0;
            case LE -> c <= 0;
            case EQ -> c == 0;
            case NE -> c != 0;
        };
    }

    // ---------------- 配置校验（返回错误列表，空表示通过） ----------------

    /**
     * 校验规则配置，返回错误列表（空表示通过）。
     * 仅做字段级静态校验，不触达存储（存储可用性与去重由服务层负责）。
     */
    public static List<String> validate(AlertRuleDO rule) {
        List<String> errors = new ArrayList<>();
        if (rule == null) {
            errors.add("规则对象不能为空");
            return errors;
        }
        if (isBlank(rule.getRuleName())) {
            errors.add("规则名称(ruleName)不能为空");
        }
        if (parseMetricKey(rule.getMetricKey()) == null) {
            errors.add("指标类型(metricKey)非法");
        }
        if (parseComparator(rule.getComparator()) == null) {
            errors.add("比较符(comparator)非法");
        }
        if (rule.getThreshold() == null) {
            errors.add("阈值(threshold)不能为空");
        }
        if (parseSeverity(rule.getSeverity()) == null) {
            errors.add("严重度(severity)非法");
        }
        if (parseChannelType(rule.getChannelType()) == null) {
            errors.add("渠道类型(channelType)非法");
        }
        if (rule.getConsecutiveCount() == null || rule.getConsecutiveCount() < 1) {
            errors.add("连续触发次数(consecutiveCount)必须 >= 1");
        }
        if (rule.getForDurationSec() == null || rule.getForDurationSec() < 0) {
            errors.add("持续时长(forDurationSec)不能为负");
        }
        if (ChannelType.WEBHOOK == parseChannelType(rule.getChannelType()) && isBlank(rule.getChannelConfig())) {
            errors.add("WEBHOOK 渠道必须配置 channelConfig");
        }
        return errors;
    }

    // ---------------- 合成告警构造 ----------------

    /**
     * 由触发中的规则构造一条合成告警条目，进入既有收件箱（复用
     * {@code AlertReceiverService#ingestSynthetic}）。
     * status=firing、instance=rules-engine，severity 取规则配置（缺省 WARNING），
     * summary 携带指标当前值与阈值关系，便于排障直接定位。
     */
    public static AlertInboxEntry buildEntry(AlertRuleDO rule, BigDecimal actual, Instant now) {
        String displayName = safe(rule.getRuleName(), "未命名规则");
        String severity = safe(rule.getSeverity(), Severity.WARNING.name());
        String metricLabel = safe(rule.getMetricKey(), "?");
        String unit = unitOf(rule.getMetricKey());
        String symbol = symbol(parseComparator(rule.getComparator()));
        String summary = String.format("规则「%s」触发：指标 %s 当前值 %s%s %s 阈值 %s%s",
                displayName, metricLabel, actual, unit, symbol, rule.getThreshold(), unit);
        return new AlertInboxEntry(
                UUID.randomUUID().toString(),
                now,
                STATUS_FIRING,
                "[规则] " + displayName,
                severity,
                summary,
                "rules-engine",
                null,
                null);
    }

    private static String symbol(Comparator c) {
        if (c == null) {
            return "";
        }
        return switch (c) {
            case GT -> ">";
            case GE -> ">=";
            case LT -> "<";
            case LE -> "<=";
            case EQ -> "==";
            case NE -> "!=";
        };
    }

    private static String unitOf(String metricKey) {
        MetricKey mk = parseMetricKey(metricKey);
        return mk == null ? "" : mk.unit();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String s, String fallback) {
        return isBlank(s) ? fallback : s;
    }
}
