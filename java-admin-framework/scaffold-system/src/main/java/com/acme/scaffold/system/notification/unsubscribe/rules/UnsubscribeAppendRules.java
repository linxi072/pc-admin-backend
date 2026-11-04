package com.acme.scaffold.system.notification.unsubscribe.rules;

/**
 * 退订链接追加规则：把渠道无关的「退订链接尾注追加」逻辑收敛为单一事实来源。
 *
 * <p>{@link com.acme.scaffold.system.notification.adapter.SmsNotificationAdapter}
 * 与 {@link com.acme.scaffold.system.notification.adapter.EmailNotificationAdapter} 原各自内联了
 * 逐字同构的「link 为空→原样返回；content + suffix + link；超长则回落原正文」逻辑，
 * 仅 suffix 文案与长度上限常量不同；此处统一口径，行为完全不变。</p>
 */
public final class UnsubscribeAppendRules {

    private UnsubscribeAppendRules() {
    }

    /**
     * 追加退订链接；link 为空或追加后超长（{@code > maxLen}）时回落原正文，
     * 不因注入链接把消息拦在长度门外。
     *
     * @param content 原正文
     * @param link    退订链接（为空表示不追加）
     * @param suffix  链接前的引导尾注（如「 退订请点击：」）
     * @param maxLen  渠道内容长度上限（超出则回落原正文）
     */
    public static String appendOrFallback(String content, String link, String suffix, int maxLen) {
        if (link == null) {
            return content;
        }
        String appended = content + suffix + link;
        return appended.length() > maxLen ? content : appended;
    }
}
