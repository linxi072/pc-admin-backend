package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 站内信渠道适配器：将渠道无关的消息转交既有 {@link MessageService} 落库 + 实时推送。
 *
 * <p>复用既有站内信投递模型（单条 / 按角色部门批量、幂等回执、在线实时提醒），
 * 不重复造轮子。消息字段原样映射为 {@link SendMessageRequest}。
 */
@Component
@RequiredArgsConstructor
public class InAppNotificationAdapter implements NotificationPort {

    private final MessageService messageService;

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.IN_APP;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        SendMessageRequest req = new SendMessageRequest(
                message.title(),
                message.content(),
                message.msgType(),
                message.receiverIds(),
                message.roleIds(),
                message.orgIds());
        return messageService.doSend(req);
    }
}
