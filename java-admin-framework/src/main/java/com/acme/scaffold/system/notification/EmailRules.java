package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 邮件渠道纯函数规则：地址校验、报文头净化、收件人归一与数量/长度约束。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link EmailNotificationAdapter} 在发送前调用，任一条不满足即抛
 * {@link BusinessException}（400），避免把非法报文交给 SMTP 或触发
 * 邮件头注入（CRLF 注入是经典钓鱼/垃圾邮件放大向量）。
 */
public final class EmailRules {

    /** 单封邮件收件人上限（防止误操作群发与 SMTP 限流）。 */
    public static final int MAX_RECIPIENTS = 50;

    /** 主题长度上限（RFC 5322 建议 ≤78 字符，此处放宽到 200 兼顾中文）。 */
    public static final int MAX_SUBJECT_LEN = 200;

    /** 正文长度上限（字符）。 */
    public static final int MAX_CONTENT_LEN = 20_000;

    /**
     * 务实地址正则：本地部分不允许 @ 与空白，域名须含点号后缀。
     * 刻意不追求 RFC 5322 完整文法——过度宽松放行注入，过度严格误拒合法地址，
     * 最终由 SMTP 服务器裁决，此处只拦截明显非法与注入企图。
     */
    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** 邮件报文头（主题/发件人）不允许的字符：CR/LF 及头部注入常用控制符。 */
    private static final Pattern HEADER_INJECTION = Pattern.compile("[\\r\\n\\t]");

    /** 连续空白折叠（净化后 \r\n 会产生相邻空格，统一为单空格）。 */
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");

    private EmailRules() {
    }

    /** 地址形态校验（trim 后）。 */
    public static boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }
        String a = address.trim();
        return !a.isEmpty() && a.length() <= 254 && ADDRESS.matcher(a).matches();
    }

    /** 净化报文头文本：去首尾空白并剔除 CR/LF/TAB（防邮件头注入），折叠连续空格，超长截断。 */
    public static String sanitizeHeader(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String cleaned = HEADER_INJECTION.matcher(text.trim()).replaceAll(" ");
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }

    /**
     * 收件人归一：trim、去空白项、保序去重、超限截断并告警。
     *
     * @return 归一后的收件人列表（非空）
     */
    public static List<String> normalizeRecipients(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "邮件收件人不能为空");
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String email : emails) {
            if (email != null && !email.isBlank()) {
                dedup.add(email.trim());
            }
        }
        if (dedup.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "邮件收件人不能为空");
        }
        if (dedup.size() > MAX_RECIPIENTS) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "邮件收件人超过单封上限 " + MAX_RECIPIENTS + "，当前 " + dedup.size());
        }
        return List.copyOf(dedup);
    }

    /**
     * 校验并规范化一封待发邮件；任一不满足抛出 400 校验异常。
     *
     * @param from    发件人（配置注入）
     * @param emails  原始收件人（来自消息）
     * @param subject 主题（原始）
     * @param content 正文（原始）
     * @return 净化后的报文草稿
     */
    public static MailDraft validateAndNormalize(String from, List<String> emails,
                                                 String subject, String content) {
        if (from == null || from.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "邮件发件人未配置（app.notifications.email.from）");
        }
        if (!isValidAddress(from)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "邮件发件人地址非法: " + from);
        }
        List<String> to = normalizeRecipients(emails);
        for (String address : to) {
            if (!isValidAddress(address)) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "邮件收件人地址非法: " + address);
            }
        }
        String cleanSubject = sanitizeHeader(subject, MAX_SUBJECT_LEN);
        if (cleanSubject.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "邮件主题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "邮件正文不能为空");
        }
        if (content.length() > MAX_CONTENT_LEN) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "邮件正文超过长度上限 " + MAX_CONTENT_LEN);
        }
        return new MailDraft(from.trim(), to, cleanSubject, content);
    }

    /** 待发邮件草稿（净化后）。 */
    public record MailDraft(String from, List<String> to, String subject, String content) {
    }
}
