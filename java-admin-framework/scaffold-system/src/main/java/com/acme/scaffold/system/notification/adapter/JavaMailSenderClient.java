package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.system.notification.config.EmailProperties;
import com.acme.scaffold.system.notification.port.MailClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;

/**
 * 基于 Spring {@link JavaMailSender} 的邮件发送实现。
 *
 * <p>每次发送按当前 {@link EmailProperties} 构建 {@link JavaMailSenderImpl}：
 * 通知为低频操作，不为连接池增加复杂度；SMTP 属性（STARTTLS / 超时 / 凭据）
 * 全部来自配置，未启用时根本不会到达本类。
 */
@Slf4j
@Component
public class JavaMailSenderClient implements MailClient {

    private final EmailProperties properties;

    public JavaMailSenderClient(EmailProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String from, List<String> to, String subject, String content) {
        JavaMailSender sender = buildSender();
        try {
            MimeMessage message = sender.createMimeMessage();
            // multipart=false：纯文本通知不需要附件；UTF-8 显式声明避免中文主题乱码
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(content, false);
            sender.send(message);
            log.info("邮件通知已发送: from={}, to={}, subject={}", from, to, subject);
        } catch (MailException e) {
            // 发送失败向上抛，由编排层/全局异常处理决定重试或记录，不静默丢弃
            log.warn("邮件通知发送失败: from={}, to={}, subject={}", from, to, subject, e);
            throw e;
        } catch (Exception e) {
            log.warn("邮件通知报文构建失败: subject={}", subject, e);
            throw new IllegalStateException("邮件报文构建失败", e);
        }
    }

    private JavaMailSender buildSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getPassword());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", properties.getUsername() != null
                && !properties.getUsername().isBlank());
        props.put("mail.smtp.starttls.enable", properties.isStarttls());
        props.put("mail.smtp.timeout", properties.getTimeoutMs());
        props.put("mail.smtp.connectiontimeout", properties.getTimeoutMs());
        return sender;
    }
}
