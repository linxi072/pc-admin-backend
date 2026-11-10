package com.acme.scaffold.realtime.listener;

import com.acme.scaffold.common.event.DomainEventListener;
import com.acme.scaffold.common.event.domain.ImMessageSentEvent;
import com.acme.scaffold.realtime.service.RealtimePushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 站内消息实时推送订阅者（ROADMAP R6 · 解耦示范）。
 *
 * <p>订阅 {@link ImMessageSentEvent}：消息落库后由 im 模块发布，本订阅者经 {@link RealtimePushService}
 * 把消息摘要实时推送给接收方在线 WebSocket 会话。im 与 realtime 经事件契约解耦，
 * 互不持有对方引用；推送失败被 {@code DefaultEventPublisher} 错误隔离，不影响消息落库。</p>
 */
@Slf4j
@Component
public class ImMessagePushListener implements DomainEventListener<ImMessageSentEvent> {

    /** 实时消息类型标识（前端据此渲染气泡）。 */
    public static final String TYPE_IM_MESSAGE = "IM_MESSAGE";

    private final ObjectProvider<RealtimePushService> pushServiceProvider;

    public ImMessagePushListener(ObjectProvider<RealtimePushService> pushServiceProvider) {
        this.pushServiceProvider = pushServiceProvider;
    }

    @Override
    public Class<ImMessageSentEvent> eventType() {
        return ImMessageSentEvent.class;
    }

    @Override
    public void onEvent(ImMessageSentEvent event) {
        RealtimePushService pushService = pushServiceProvider.getIfAvailable();
        if (pushService == null) {
            return;
        }
        for (Long userId : event.getReceiverIds()) {
            int delivered = pushService.sendToUser(userId, TYPE_IM_MESSAGE, Map.of(
                    "conversationId", event.getConversationId(),
                    "messageId", event.getMessageId(),
                    "senderId", event.getSenderId(),
                    "conversationType", event.getConversationType(),
                    "preview", event.getContentPreview()));
            if (delivered == 0) {
                log.debug("IM 消息接收方离线或未启用网关 userId={} messageId={}",
                        userId, event.getMessageId());
            }
        }
    }
}
