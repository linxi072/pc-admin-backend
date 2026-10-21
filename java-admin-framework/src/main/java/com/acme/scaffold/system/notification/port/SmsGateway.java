package com.acme.scaffold.system.notification.port;

import com.acme.scaffold.system.notification.rules.SmsRules;

/**
 * 短信网关客户端契约，便于离线单测用假实现替换真实网关（与 {@link WebhookClient} 同模式）。
 *
 * <p>两个开箱实现按 {@code app.notifications.sms.provider} 条件装配（互斥）：
 * {@code LOG}（默认，开发日志通道）与 {@code HTTP}（通用 HTTP 网关）；
 * 对接具体厂商时可新增实现并扩展 provider 枚举，渠道编排层无感。
 */
public interface SmsGateway {

    /**
     * 发送一条短信（草稿已通过 {@link SmsRules} 校验：号码合法非空、内容长度合规）。
     *
     * @param draft 待发草稿（{@code mobiles} 已归一为 11 位，{@code content} 已拼接签名）
     */
    void send(SmsRules.SmsDraft draft);
}
