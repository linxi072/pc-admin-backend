package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

/**
 * 通知适配器共享辅助：收敛各简单渠道（Webhook / 短信 / 邮件）重复的「渠道启用校验」idiom。
 *
 * <p>机器人适配器（Wecom / Dingtalk）因开关经参数中心热更新、未启用文案可配置，
 * 仍保留各自 {@code isEnabled()} 与 {@code disabledMessage}，不强行纳入本辅助。</p>
 */
final class NotificationAdapterSupport {

    private NotificationAdapterSupport() {
    }

    /** 渠道未启用时抛 {@code VALIDATION_ERROR}；消息形如 {@code "<渠道名> 通知渠道未启用"}。 */
    static void requireChannelEnabled(boolean enabled, String channelLabel) {
        if (!enabled) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, channelLabel + " 通知渠道未启用");
        }
    }
}
