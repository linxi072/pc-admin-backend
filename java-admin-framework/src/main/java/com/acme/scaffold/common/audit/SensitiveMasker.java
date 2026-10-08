package com.acme.scaffold.common.audit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审计日志脱敏：对外暴露操作日志时，抹除凭证与个人敏感信息。
 *
 * <p><b>存在意义</b>：审计摘要（request_summary / result_summary）由 {@link AuditAspect} 在切面里
 * 原样落库，其中可能包含登录密码、Token、手机号、邮箱、身份证等。日志本身允许保留原文（排障需要），
 * 但<b>查询接口对外返回时必须脱敏</b>——设计方案 §20 明确要求「审计日志可按 traceId 查询且无敏感字段」。
 *
 * <p><b>实现取舍</b>：这里用正则而非 Jackson 反序列化，原因是摘要是不定结构的自由文本（可能是
 * 参数拼接串而非严格 JSON），正则对「半结构化文本」更容错，且无任何额外依赖、便于纯函数单测。
 * 顺序很重要：先按「键名」抹掉整段凭证值（避免其中的数字被后续的手机号/身份证规则二次处理），
 * 再按「值形态」抹 PII。
 */
public final class SensitiveMasker {

    /** 统一掩码。 */
    private static final String MASK = "******";

    /**
     * 凭证类键名（不区分大小写）。命中后整段值替换为 {@link #MASK}。
     * 覆盖字符串值与数字/布尔/null 两类 JSON 字面量形态。
     */
    private static final Pattern CREDENTIAL_KEYS = Pattern.compile(
            // [_-]? 兼容 snake_case / kebab-case / camelCase 三种命名风格；长键名排在前面，
            // 避免 "access_token" 被更短的 "token" 先匹配掉导致漏脱敏
            "\"(refresh[_-]?token|access[_-]?token|old[_-]?password|new[_-]?password"
                    + "|confirm[_-]?password|client[_-]?secret|private[_-]?key|auth[_-]?code"
                    + "|sms[_-]?code|password|passwd|pwd|token|jwt|secret|authorization|captcha|pin)"
                    + "\"\\s*:\\s*",
            Pattern.CASE_INSENSITIVE);

    /** JSON 字符串值（含转义）。 */
    private static final Pattern STRING_VALUE = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    /** JSON 字面量值：数字 / true / false / null。 */
    private static final Pattern LITERAL_VALUE = Pattern.compile("(-?\\d+(?:\\.\\d+)?|true|false|null)");

    /** 中国大陆手机号。 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    /** 邮箱。 */
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    /** 身份证（18 位，末位可能为 X）。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{14})([\\dXx]{4})(?!\\d)");
    /** IPv4。 */
    private static final Pattern IPV4 = Pattern.compile("(?<!\\d)(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?!\\d)");

    private SensitiveMasker() {
    }

    // ------------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------------

    /**
     * 脱敏审计摘要（请求/响应）。null 与空白原样返回。
     *
     * @param text 原始摘要文本
     * @return 抹除凭证与 PII 后的文本
     */
    public static String maskSummary(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String out = maskCredentialValues(text);
        out = PHONE.matcher(out).replaceAll(mr -> mr.group(1).substring(0, 3) + "****" + mr.group(1).substring(7));
        out = EMAIL.matcher(out).replaceAll(mr -> maskEmail(mr.group(0)));
        out = ID_CARD.matcher(out).replaceAll(mr -> "**************" + mr.group(2));
        return out;
    }

    /** 手机号脱敏：138****1234；非手机号原样返回。 */
    public static String maskPhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 邮箱脱敏：保留首字符与域名，如 {@code z***@example.com}。 */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        Matcher m = EMAIL.matcher(email);
        if (!m.matches()) {
            return email;
        }
        String local = m.group(1);
        String head = local.substring(0, 1);
        return head + "***@" + m.group(2);
    }

    /** 身份证脱敏：保留后 4 位。 */
    public static String maskIdCard(String idCard) {
        if (idCard == null) {
            return null;
        }
        Matcher m = ID_CARD.matcher(idCard);
        if (!m.matches()) {
            return idCard;
        }
        return "**************" + m.group(2);
    }

    /**
     * IP 脱敏：IPv4 保留前两段（{@code 192.168.*.*}），IPv6 与非 IP 文本整体掩码。
     * 保留网段是为了让审计仍能判断来源区域，同时不暴露具体主机。
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        Matcher m = IPV4.matcher(ip.trim());
        if (m.matches()) {
            return m.group(1) + "." + m.group(2) + ".*.*";
        }
        // 非 IPv4（含 IPv6、unknown、代理链 "a, b"）一律整体掩码，避免误泄漏
        return MASK;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 按键名定位凭证字段并替换其值：支持字符串值与字面量值两种形态。 */
    private static String maskCredentialValues(String text) {
        Matcher keyMatcher = CREDENTIAL_KEYS.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;
        while (keyMatcher.find()) {
            int valueStart = keyMatcher.end();
            sb.append(text, cursor, valueStart);
            String replaced = maskValueAt(text, valueStart);
            if (replaced == null) {
                // 值形态未识别：不做替换，避免破坏结构
                sb.append(text, valueStart, valueStart);
                cursor = valueStart;
                continue;
            }
            sb.append(replaced);
            cursor = valueStart + consumedLength(text, valueStart);
        }
        if (cursor == 0) {
            return text;
        }
        sb.append(text, cursor, text.length());
        return sb.toString();
    }

    /** 返回从 {@code start} 起的值的掩码文本；无法识别时返回 null。 */
    private static String maskValueAt(String text, int start) {
        if (start >= text.length()) {
            return null;
        }
        if (text.charAt(start) == '"') {
            Matcher m = STRING_VALUE.matcher(text).region(start, text.length());
            if (m.lookingAt()) {
                return "\"" + MASK + "\"";
            }
            return null;
        }
        Matcher m = LITERAL_VALUE.matcher(text).region(start, text.length());
        if (m.lookingAt()) {
            return "\"" + MASK + "\"";
        }
        return null;
    }

    /** 被替换掉的原文长度，用于推进游标。 */
    private static int consumedLength(String text, int start) {
        if (start >= text.length()) {
            return 0;
        }
        if (text.charAt(start) == '"') {
            Matcher m = STRING_VALUE.matcher(text).region(start, text.length());
            return m.lookingAt() ? m.end() - start : 0;
        }
        Matcher m = LITERAL_VALUE.matcher(text).region(start, text.length());
        return m.lookingAt() ? m.end() - start : 0;
    }
}
