package com.acme.scaffold.common.notification;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.List;

/**
 * 纯函数路由：按渠道从已注册适配器中选出第一个 {@link NotificationPort#supports} 命中的实现。
 *
 * <p>无命中时抛出校验异常，避免通知被静默丢弃——调用方必须显式处理「渠道未实现」这一错误。
 * 该函数无副作用、不依赖 Spring 上下文，可直接离线单测。
 */
public final class NotificationRouter {

    private NotificationRouter() {
    }

    public static NotificationPort resolve(Channel channel, List<NotificationPort> ports) {
        return ports.stream()
                .filter(p -> p.supports(channel))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "不支持的通知渠道: " + channel));
    }
}
