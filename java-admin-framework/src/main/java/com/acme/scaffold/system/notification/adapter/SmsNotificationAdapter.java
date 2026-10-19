package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.SmsProperties;
import com.acme.scaffold.system.notification.port.SmsGateway;
import com.acme.scaffold.system.notification.rules.SmsRules;
import com.acme.scaffold.system.smslog.SmsRecordRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
 */
@Component
@RequiredArgsConstructor
public class SmsNotificationAdapter implements NotificationPort {

    private final SmsGateway gateway;
    private final SmsProperties properties;
    private final SmsRecordRecorder recorder;

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
            gateway.send(draft);
            recorder.record(batchNo, draft.mobiles(), draft.content(), provider, true, null);
            return draft.mobiles().size();
        } catch (RuntimeException ex) {
            // 网关失败同样留痕（状态 FAILED + 异常消息），再向上抛出不掩盖投递失败
            recorder.record(batchNo, draft.mobiles(), draft.content(), provider, false, ex.getMessage());
            throw ex;
        }
    }
}
