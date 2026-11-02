package com.acme.scaffold.system.rules;

import com.acme.scaffold.common.notification.Channel;

/**
 * 渠道名归一共享规则。
 *
 * <p>{@code NotificationTemplateRules} 与 {@code NotificationRecordRules} 原本各自内联了一份完全相同的
 * 渠道名归一逻辑（大小写不敏感匹配 {@link Channel} 枚举，非法返回 {@code null}）；此处收敛为单一事实来源，
 * 两规则类保留同名公开方法作为委托壳。</p>
 */
public final class ChannelNormalizeRules {

    private ChannelNormalizeRules() {
    }

    /** 渠道归一：大小写不敏感匹配 {@link Channel} 枚举，非法返回 {@code null}。 */
    public static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String c = channel.trim().toUpperCase();
        for (Channel value : Channel.values()) {
            if (value.name().equals(c)) {
                return c;
            }
        }
        return null;
    }
}
