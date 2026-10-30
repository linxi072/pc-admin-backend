package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.EmailProperties;
import com.acme.scaffold.system.notification.port.MailClient;
import com.acme.scaffold.system.notification.rules.EmailRules;
import com.acme.scaffold.system.notification.unsubscribe.NotificationUnsubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 邮件渠道适配器：把渠道无关消息经 SMTP 发送给 {@code emails} 收件人列表。
 *
 * <p>与 Webhook 适配器一致的门禁策略：仅当 {@code app.notifications.email.enabled=true}
 * 才发送，否则抛校验异常，避免通知被静默丢弃。发送前经 {@link EmailRules}
 * 完成地址校验、CRLF 净化与数量约束，非法报文不外发。</p>
 *
 * <p>本渠道忽略 {@code receiverIds}/{@code roleIds}/{@code orgIds}——邮件按地址维度投递，
 * 与站内信的「按用户/角色/部门批量落库」语义不同（调用方需自行把用户解析为邮箱地址）。</p>
 *
 * <p><b>M15 退订链接注入</b>：退订功能激活（{@code app.notifications.unsubscribe.enabled}
 * 且密钥/基址齐备）且收件地址能反查到用户时，按收件人<b>逐封个性化</b>发送——每人正文尾部
 * 追加各自的 HMAC 签名退订链接（令牌按用户铸造，链接不可互用）；无匹配用户的外部地址
 * 与原批量路径一样不带链接。未激活或全军覆没时保持原有「一封群发」路径，行为零变化。</p>
 */
@Component
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationPort {

    /** 退订链接注入的正文尾注（含引导语，邮件正文无长度硬约束，仍以规则上限兜底）。 */
    private static final String UNSUBSCRIBE_FOOTER = "\n\n如不想再收到此类邮件，可点击以下链接退订：";

    private final MailClient mailClient;
    private final EmailProperties properties;
    private final NotificationUnsubscribeService unsubscribeService;

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
        Map<String, String> links =
                unsubscribeService.resolveLinks(draft.to(), true, Channel.EMAIL, message.msgType());
        if (links.isEmpty()) {
            mailClient.send(draft.from(), draft.to(), draft.subject(), draft.content());
            return draft.to().size();
        }
        // 逐收件人个性化：链接按用户铸造，键为归一化（小写）地址
        for (String to : draft.to()) {
            String link = links.get(to.toLowerCase(Locale.ROOT));
            String content = withUnsubscribeFooter(draft.content(), link);
            mailClient.send(draft.from(), List.of(to), draft.subject(), content);
        }
        return draft.to().size();
    }

    /** 追加退订尾注；无链接或追加后超长（规则上限）时回落原正文，不因注入把邮件拦在门外。 */
    private static String withUnsubscribeFooter(String content, String link) {
        if (link == null) {
            return content;
        }
        String withFooter = content + UNSUBSCRIBE_FOOTER + link;
        return withFooter.length() > EmailRules.MAX_CONTENT_LEN ? content : withFooter;
    }
}
