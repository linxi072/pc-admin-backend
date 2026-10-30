package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.SmsProperties;
import com.acme.scaffold.system.notification.port.SmsGateway;
import com.acme.scaffold.system.notification.rules.SmsRules;
import com.acme.scaffold.system.notification.unsubscribe.NotificationUnsubscribeService;
import com.acme.scaffold.system.smslog.SmsRecordRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 短信渠道适配器：把渠道无关消息经 {@link SmsGateway} 发送给 {@code mobiles} 手机号列表。
 *
 * <p>与邮件适配器一致的门禁策略：仅当 {@code app.notifications.sms.enabled=true}
 * 才发送，否则抛校验异常，避免通知被静默丢弃（短信按条计费，静默失败不可接受）。
 * 发送前经 {@link SmsRules} 完成号码校验、内容长度约束与签名拼接，非法报文不外发。</p>
 *
 * <p>本渠道忽略 {@code receiverIds}/{@code roleIds}/{@code orgIds}/{@code title}——
 * 短信按号码维度投递且无主题，调用方需自行把用户解析为手机号。</p>
 *
 * <p><b>发送记录</b>：校验通过并生成批次号后，无论网关成功还是失败均落一条
 * {@code sys_sms_record}（经 {@link SmsRecordRecorder}，独立事务、脱敏存储），
 * 供「短信记录」页对账排查；校验不合法的报文未尝试发送，不产生记录。</p>
 *
 * <p><b>M15 退订链接注入</b>：退订功能激活且手机号能反查到用户时，按号码<b>逐条个性化</b>
 * 发送（每人内容尾部追加各自的 HMAC 签名退订链接）；短信按条计费、逐条投递，
 * 拆循环不改变计费模型，仅把批量合并的网关调用拆开。追加后超长的该条回落原正文
 * （{@link SmsRules#MAX_CONTENT_LEN}）——不能因注入链接把短信拦在长度门外。
 * 未激活或全军覆没时保持原有「一次批量」路径，行为零变化。</p>
 */
@Component
@RequiredArgsConstructor
public class SmsNotificationAdapter implements NotificationPort {

    private final SmsGateway gateway;
    private final SmsProperties properties;
    private final SmsRecordRecorder recorder;
    private final NotificationUnsubscribeService unsubscribeService;

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
        String batchNo = SmsRecordRecorder.newBatchNo();
        String provider = properties.getProvider().name();
        try {
            Map<String, String> links =
                    unsubscribeService.resolveLinks(draft.mobiles(), false, Channel.SMS, message.msgType());
            if (links.isEmpty()) {
                gateway.send(draft);
            } else {
                for (String mobile : draft.mobiles()) {
                    gateway.send(new SmsRules.SmsDraft(List.of(mobile),
                            withUnsubscribeSuffix(draft.content(), links.get(mobile))));
                }
            }
            recorder.record(batchNo, draft.mobiles(), draft.content(), provider, true, null);
            return draft.mobiles().size();
        } catch (RuntimeException ex) {
            // 网关失败同样留痕（状态 FAILED + 异常消息），再向上抛出不掩盖投递失败
            recorder.record(batchNo, draft.mobiles(), draft.content(), provider, false, ex.getMessage());
            throw ex;
        }
    }

    /** 追加退订链接后缀；无链接或超长（规则上限）时回落原正文。 */
    private static String withUnsubscribeSuffix(String content, String link) {
        if (link == null) {
            return content;
        }
        String withLink = content + " 退订请点击：" + link;
        return withLink.length() > SmsRules.MAX_CONTENT_LEN ? content : withLink;
    }
}
