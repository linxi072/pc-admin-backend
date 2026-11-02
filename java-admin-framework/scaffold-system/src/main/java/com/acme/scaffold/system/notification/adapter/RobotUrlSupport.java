package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.NotificationMessage;

/**
 * 机器人适配器（钉钉 / 企业微信）共享的 Webhook 地址解析逻辑。
 *
 * <p>两适配器原本各自内联了一份完全相同的地址回落逻辑（消息自带 → 参数中心 → 配置默认值 → 缺失报错），
 * 仅缺失时报错的提示文案不同；此处收敛为单一事实来源，缺失文案作为参数透传。</p>
 */
final class RobotUrlSupport {

    private RobotUrlSupport() {
    }

    /** 解析 Webhook 地址：消息自带优先，其次参数中心，最后配置默认值；三者皆空则抛出带 {@code missingMessage} 的异常。 */
    static String resolveUrl(NotificationMessage message, String configuredUrl, String fallbackUrl,
                             String missingMessage) {
        String url = (message.webhookUrl() != null && !message.webhookUrl().isBlank())
                ? message.webhookUrl()
                : configuredUrl;
        if (url == null || url.isBlank()) {
            url = fallbackUrl;
        }
        if (url == null || url.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, missingMessage);
        }
        return url;
    }
}
