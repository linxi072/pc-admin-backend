package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.EmailProperties;
import com.acme.scaffold.system.notification.port.MailClient;
import com.acme.scaffold.system.notification.rules.EmailRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 邮件渠道适配器：把渠道无关消息经 SMTP 发送给 {@code emails} 收件人列表。
 *
 * <p>与 Webhook 适配器一致的门禁策略：仅当 {@code app.notifications.email.enabled=true}
 * 才发送，否则抛校验异常，避免通知被静默丢弃。发送前经 {@link EmailRules}
 * 完成地址校验、CRLF 净化与数量约束，非法报文不外发。
 *
 * <p>本渠道忽略 {@code receiverIds}/{@code roleIds}/{@code orgIds}——邮件按地址维度投递，
 * 与站内信的「按用户/角色/部门批量落库」语义不同（调用方需自行把用户解析为邮箱地址）。
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationPort {

    private final MailClient mailClient;
    private final EmailProperties properties;

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.EMAIL;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        if (!properties.isEnabled()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "邮件通知渠道未启用");
        }
        EmailRules.MailDraft draft = EmailRules.validateAndNormalize(
                properties.getFrom(), message.emails(), message.title(), message.content());
        mailClient.send(draft.from(), draft.to(), draft.subject(), draft.content());
        return draft.to().size();
    }
}
