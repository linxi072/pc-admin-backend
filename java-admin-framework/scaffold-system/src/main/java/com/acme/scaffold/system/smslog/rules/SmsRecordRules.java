package com.acme.scaffold.system.smslog.rules;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.system.notification.config.SmsProperties;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 短信发送记录纯函数规则：号码掩码、正文摘要、筛选值归一。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link com.acme.scaffold.system.smslog.SmsRecordRecorder}（落库前脱敏）与
 * {@link com.acme.scaffold.system.smslog.SmsRecordService}（筛选值收敛）复用，
 * 保证「存什么」与「查什么」口径同源。</p>
 */
public final class SmsRecordRules {

    /** 结果码：成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 结果码：失败（网关异常或发送被拒）。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 正文摘要长度上限（列宽 256，预留省略号空间）。 */
    public static final int MAX_SUMMARY_LEN = 200;

    /** 摘要内折叠的空白/控制符（防日志注入与换行破坏表格）。 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t]");

    private SmsRecordRules() {
    }

    /**
     * 单个手机号脱敏：trim 后复用 {@link SensitiveMasker#maskPhone}（{@code 138****8000}）；
     * 非号码形态原样返回（审计可读性优先），null 归一为空串。
     */
    public static String maskMobile(String mobile) {
        if (mobile == null) {
            return "";
        }
        return SensitiveMasker.maskPhone(mobile.trim());
    }

    /**
     * 手机号列表掩码拼接（保序，逗号分隔）；空列表返回空串。
     * 拼接结果可能超过列宽，由调用方截断——号码数始终以调用方传入的列表大小为准。
     */
    public static String maskMobiles(List<String> mobiles) {
        if (mobiles == null || mobiles.isEmpty()) {
            return "";
        }
        return mobiles.stream().map(SmsRecordRules::maskMobile).collect(Collectors.joining(","));
    }

    /**
     * 正文摘要：trim → CR/LF/TAB 折叠为空格 → 连续空格合并 → 超长截断并追加省略号。
     * 空白正文返回空串（不落 {@code "…"} 噪声）。
     */
    public static String summarizeContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String flat = CONTROL_CHARS.matcher(content.trim()).replaceAll(" ").replaceAll(" +", " ").trim();
        if (flat.isEmpty()) {
            return "";
        }
        if (flat.length() <= MAX_SUMMARY_LEN) {
            return flat;
        }
        return flat.substring(0, MAX_SUMMARY_LEN) + "…";
    }

    /**
     * 结果筛选值归一：仅接受 {@value #STATUS_SUCCESS} / {@value #STATUS_FAILED}（大小写不敏感），
     * 其余（含空）返回 {@code null}——调用方据此忽略该筛选条件，避免把非法值带入 SQL。
     */
    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String s = status.trim().toUpperCase();
        return STATUS_SUCCESS.equals(s) || STATUS_FAILED.equals(s) ? s : null;
    }

    /**
     * 网关筛选值归一：按 {@link SmsProperties.Provider} 枚举名匹配（大小写不敏感），
     * 未知提供方返回 {@code null}（忽略筛选），新增网关实现时无需改动本方法。
     */
    public static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        String p = provider.trim().toUpperCase();
        for (SmsProperties.Provider value : SmsProperties.Provider.values()) {
            if (value.name().equals(p)) {
                return p;
            }
        }
        return null;
    }

    /** 通用截断：null 原样返回，超长截取前 {@code max} 字符（不追加省略号，用于定宽列）。 */
    public static String truncate(String text, int max) {
        if (text == null || max <= 0) {
            return max <= 0 ? "" : text;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
