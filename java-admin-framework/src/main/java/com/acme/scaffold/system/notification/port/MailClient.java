package com.acme.scaffold.system.notification.port;

import java.util.List;

/**
 * 邮件发送客户端契约，便于离线单测用假实现替换真实 SMTP 通道（与 {@link WebhookClient} 同模式）。
 */
public interface MailClient {

    /**
     * 发送一封纯文本邮件。
     *
     * @param from    发件人地址
     * @param to      收件人地址列表（已通过 {@link EmailRules} 校验，非空）
     * @param subject 主题（已做 CRLF 净化）
     * @param content 正文
     */
    void send(String from, List<String> to, String subject, String content);
}
