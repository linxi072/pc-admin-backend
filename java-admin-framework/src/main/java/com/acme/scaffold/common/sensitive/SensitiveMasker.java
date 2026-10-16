package com.acme.scaffold.common.sensitive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏：对外暴露数据时抹除凭证与个人敏感信息（框架级通用能力）。
 *
 * <p><b>存在意义</b>：审计摘要、登录日志、导出文件等对外数据中可能包含登录密码、Token、手机号、
 * 邮箱、身份证、银行卡号等。存储层允许保留原文（排障需要），但<b>对外返回或落盘导出时必须脱敏</b>——
 * 设计方案 §20 明确要求「审计日志可按 traceId 查询且无敏感字段」。</p>
 *
 * <p><b>实现取舍</b>：这里用正则而非 Jackson 反序列化，原因是摘要是不定结构的自由文本（可能是
 * 参数拼接串而非严格 JSON），正则对「半结构化文本」更容错，且无任何额外依赖、便于纯函数单测。
 * 顺序很重要：先按「键名」抹掉整段凭证值（避免其中的数字被后续的手机号/身份证规则二次处理），
 * 再按「值形态」抹 PII。</p>
 *
 * <p><b>包位置演进</b>：本类原位于 {@code common/audit}（仅服务审计日志），随着登录日志、导出中心
 * 等场景复用需求出现，提升为 {@code common/sensitive} 框架级通用能力；所有既有静态方法
 * 签名与行为保持不变（向后兼容），仅新增更细粒度的策略与统一分派入口。</p>
 */
public final class SensitiveMasker {

    /** 统一掩码。 */
    private static final String MASK = "******";

    /** 地址保留的前缀长度（通常足以覆盖省市区，掩去门牌等精确定位信息）。 */
    private static final int ADDRESS_KEEP = 6;

    /** 姓名保留首字符后最多追加的星号个数（避免超长掩码影响可读性）。 */
    private static final int NAME_MAX_STARS = 2;

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
    /** 银行卡号（12~19 位连续数字；前后不得紧邻其它数字，避免把长订单号误判为卡号）。 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(\\d{12,19})(?!\\d)");
    /** IPv4。 */
    private static final Pattern IPV4 = Pattern.compile("(?<!\\d)(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?!\\d)");

    private SensitiveMasker() {
    }

    // ------------------------------------------------------------------
    // 对外 API
    // ------------------------------------------------------------------

    /**
     * 统一分派：按 {@link SensitiveType} 选择脱敏策略。
     *
     * <p>null 值或 {@link SensitiveType#NONE} 原样返回；各策略对「不匹配形态」的输入亦原样返回，
     * 因此对已脱敏/占位文本重复调用是幂等的、无害的。</p>
     *
     * @param value 原始值
     * @param type  敏感类型（null 视为不脱敏）
     * @return 脱敏后的值
     */
    public static String mask(String value, SensitiveType type) {
        if (value == null || type == null) {
            return value;
        }
        return switch (type) {
            case MOBILE -> maskPhone(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case IP -> maskIp(value);
            case CREDENTIAL_TEXT -> maskSummary(value);
            case NONE -> value;
        };
    }

    /**
     * 脱敏审计摘要（请求/响应等自由文本）。null 与空白原样返回。
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
     * 银行卡号脱敏：保留后 4 位，其余数字以 {@code *} 等长替换（保持位数便于对账核对）。
     * 非「12~19 位连续数字」原样返回。
     */
    public static String maskBankCard(String bankCard) {
        if (bankCard == null) {
            return null;
        }
        Matcher m = BANK_CARD.matcher(bankCard);
        if (!m.matches()) {
            return bankCard;
        }
        String digits = m.group(1);
        int keep = 4;
        return "*".repeat(digits.length() - keep) + digits.substring(digits.length() - keep);
    }

    /**
     * 姓名脱敏：保留首字符，其余以 {@code *} 替换（最多 {@value #NAME_MAX_STARS} 个）。
     * 空白/单字符原样返回（无从掩码）。
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String trimmed = name.trim();
        if (trimmed.length() <= 1) {
            return trimmed;
        }
        return trimmed.charAt(0) + "*".repeat(Math.min(trimmed.length() - 1, NAME_MAX_STARS));
    }

    /**
     * 地址脱敏：保留前 {@value #ADDRESS_KEEP} 个字符（通常到区/县一级），其余以 {@code ****} 替代。
     * 短于保留长度时原样返回。
     */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        String trimmed = address.trim();
        if (trimmed.length() <= ADDRESS_KEEP) {
            return trimmed;
        }
        return trimmed.substring(0, ADDRESS_KEEP) + "****";
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
