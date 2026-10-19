package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.SmsProperties;
import com.acme.scaffold.system.notification.port.SmsGateway;
import com.acme.scaffold.system.notification.rules.SmsRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 短信渠道适配器：把渠道无关消息经 {@link SmsGateway} 发送给 {@code mobiles} 手机号列表。
 *
 * <p>与邮件适配器一致的门禁策略：仅当 {@code app.notifications.sms.enabled=true}
 * 才发送，否则抛校验异常，避免通知被静默丢弃（短信按条计费，静默失败不可接受）。
 * 发送前经 {@link SmsRules} 完成号码校验、内容长度约束与签名拼接，非法报文不外发。
 *
 * <p>本渠道忽略 {@code receiverIds}/{@code roleIds}/{@code orgIds}/{@code title}——
 * 短信按号码维度投递且无主题，调用方需自行把用户解析为手机号。
 */
@Component
@RequiredArgsConstructor
public class SmsNotificationAdapter implements NotificationPort {

    private final SmsGateway gateway;
    private final SmsProperties properties;

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.SMS;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        if (!properties.isEnabled()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "短信通知渠道未启用");
        }
        SmsRules.SmsDraft draft = SmsRules.validateAndNormalize(
                message.mobiles(), message.content(), properties.getSignature());
        gateway.send(draft);
        return draft.mobiles().size();
    }
}
