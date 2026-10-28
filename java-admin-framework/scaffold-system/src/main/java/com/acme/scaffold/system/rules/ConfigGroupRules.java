package com.acme.scaffold.system.rules;

import java.util.Collections;
import java.util.Map;

/**
 * 系统参数分组规则（M36，纯函数、无 Spring 依赖）：按 {@code config_key} 的命名空间前缀聚合。
 *
 * <p>现有种子键已采用 {@code 命名空间.子项} 形式（如 {@code security.jwt.issuer}、
 * {@code notification.wecom.enabled}），取首个 {@code .} 之前的部分即为其所属分组。
 * 分组仅用于界面分区与聚合返回，不改表结构（严守 Flyway 迁移不可改铁律）。</p>
 */
public final class ConfigGroupRules {

    /** 已知分组 → 中文展示名。未知分组回退为原始前缀。 */
    private static final Map<String, String> GROUP_LABELS = Map.ofEntries(
            Map.entry("security", "安全"),
            Map.entry("cache", "缓存"),
            Map.entry("notification", "通知"),
            Map.entry("export", "导入导出"),
            Map.entry("import", "导入导出"),
            Map.entry("captcha", "验证码"),
            Map.entry("antireplay", "防重放"),
            Map.entry("client-ip", "客户端IP"),
            Map.entry("observability", "可观测性"),
            Map.entry("secret", "密钥源"),
            Map.entry("ratelimit", "限流"));

    private static final String OTHER = "other";

    private ConfigGroupRules() {
    }

    /** 取 {@code config_key} 的命名空间前缀（首个 {@code .} 之前）。无前缀返回 {@code "other"}。 */
    public static String deriveGroup(String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return OTHER;
        }
        int dot = configKey.indexOf('.');
        return dot < 0 ? OTHER : configKey.substring(0, dot);
    }

    /** 分组中文展示名；未知分组回退为前缀本体。 */
    public static String groupLabel(String group) {
        if (group == null) {
            return GROUP_LABELS.getOrDefault(OTHER, OTHER);
        }
        return GROUP_LABELS.getOrDefault(group, group);
    }

    /** 用于排序的稳定分组顺序（按英文前缀字典序）；导出为不可变视图。 */
    public static Map<String, String> groupLabels() {
        return Collections.unmodifiableMap(GROUP_LABELS);
    }
}
