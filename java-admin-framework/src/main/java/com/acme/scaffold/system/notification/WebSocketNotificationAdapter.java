package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.realtime.RealtimeNotificationPayload;
import com.acme.scaffold.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket 实时推送渠道适配器：把渠道无关的消息实时推送到在线会话。
 *
 * <p>复用既有 {@link RealtimePushService}：指定 {@code receiverIds} 时定向推送给在线用户，
 * 否则向所有在线会话广播。{@code roleIds}/{@code orgIds} 本渠道忽略（实时会话按 userId 维度，
 * 与站内信的「按角色/部门批量落库」语义不同）。
 *
 * <p>与站内信/Webhook 适配器并列，被 Spring 自动收集后由 {@code NotificationService} 统一编排，
 * 新增本适配器无需改动编排层与控制器。
 */
@Component
@RequiredArgsConstructor
public class WebSocketNotificationAdapter implements NotificationPort {

    private final RealtimePushService realtimePushService;

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.WEBSOCKET;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        RealtimeNotificationPayload payload = RealtimeNotificationPayload.of(
                message.title(), message.content(), message.msgType(), message.extra());
        List<Long> receivers = message.receiverIds();
        if (receivers != null && !receivers.isEmpty()) {
            return realtimePushService.sendToUsers(receivers, RealtimePushService.TYPE_NOTIFICATION, payload);
        }
        return realtimePushService.broadcast(RealtimePushService.TYPE_NOTIFICATION, payload);
    }
}
