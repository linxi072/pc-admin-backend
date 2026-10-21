package com.acme.scaffold.system.notification.rules;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 短信渠道纯函数规则：手机号校验、内容与签名约束、草稿组装。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link com.acme.scaffold.system.notification.adapter.SmsNotificationAdapter}
 * 在发送前调用，任一条不满足即抛 {@link BusinessException}（400），
 * 避免把非法号码或超长内容交给短信网关（网关按条计费，错误群发直接产生费用）。
 *
 * <p>号码形态务实处理：容忍 {@code +86}/{@code 86} 国家码前缀与空格/连字符分隔
 * （用户从通讯录复制的常见形态），归一为 11 位大陆手机号后校验；
 * 国际号段不在范围内，由网关侧扩展。
 */
public final class SmsRules {

    /** 单次群发手机号上限（防止误操作群发与网关限流）。 */
    public static final int MAX_MOBILES = 100;

    /** 短信正文字符上限（超出由网关拆分长短信计费，此处只拦截明显异常）。 */
    public static final int MAX_CONTENT_LEN = 500;

    /** 短信签名长度上限（如【Acme】，含方括号）。 */
    public static final int MAX_SIGNATURE_LEN = 32;

    /** 大陆手机号：1 开头、第二位 3-9、共 11 位数字。 */
    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 报文注入常用控制符（签名/内容进入网关 JSON 前净化）。 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t]");

    private SmsRules() {
    }

    /**
     * 手机号形态校验（归一后）：trim、去空格/连字符、剥离可选 +86/86 前缀，
     * 须为 11 位大陆手机号。
     */
    public static boolean isValidMobile(String mobile) {
        if (mobile == null) {
            return false;
        }
        return normalizeMobile(mobile) != null;
    }

    /**
     * 归一化单个手机号：trim → 去内部空格/连字符 → 剥离 {@code +86}/{@code 86} 前缀；
     * 非法返回 {@code null}（便于调用方区分「无输入」与「输入非法」）。
     */
    public static String normalizeMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        String m = mobile.trim().replace(" ", "").replace("-", "");
        if (m.startsWith("+86")) {
            m = m.substring(3);
        } else if (m.startsWith("86") && m.length() == 13) {
            m = m.substring(2);
        }
        return MOBILE.matcher(m).matches() ? m : null;
    }

    /**
     * 手机号归一：trim、去空白项、保序去重、逐一号码校验、超限拒绝。
     *
     * @return 归一后的手机号列表（非空，均为 11 位）
     */
    public static List<String> normalizeMobiles(List<String> mobiles) {
        if (mobiles == null || mobiles.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "短信接收手机号不能为空");
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String mobile : mobiles) {
            if (mobile != null && !mobile.isBlank()) {
                String normalized = normalizeMobile(mobile);
                if (normalized == null) {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                            "手机号格式非法: " + mobile.trim());
                }
                dedup.add(normalized);
            }
        }
        if (dedup.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "短信接收手机号不能为空");
        }
        if (dedup.size() > MAX_MOBILES) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "短信接收号码超过单次上限 " + MAX_MOBILES + "，当前 " + dedup.size());
        }
        return List.copyOf(dedup);
    }

    /**
     * 校验并组装待发短信草稿；任一不满足抛出 400 校验异常。
     *
     * @param mobiles   原始手机号列表（来自消息）
     * @param content   短信正文（原始）
     * @param signature 短信签名（配置注入，可为空；非空时净化为 {@code 【签名】正文} 形态）
     * @return 净化后的草稿（{@code content} 已含签名拼接结果）
     */
    public static SmsDraft validateAndNormalize(List<String> mobiles, String content, String signature) {
        List<String> to = normalizeMobiles(mobiles);
        if (content == null || content.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "短信内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LEN) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "短信内容超过长度上限 " + MAX_CONTENT_LEN);
        }
        return new SmsDraft(to, appendSignature(content, signature));
    }

    /**
     * 签名拼接与净化：剔除 CR/LF/TAB（防注入），限长截断；
     * 空签名原样返回正文。形态为 {@code 【签名】正文}（缺方括号时补齐）。
     */
    public static String appendSignature(String content, String signature) {
        if (signature == null || signature.isBlank()) {
            return content;
        }
        String sign = CONTROL_CHARS.matcher(signature.trim()).replaceAll("").trim();
        if (sign.isEmpty()) {
            return content;
        }
        if (sign.length() > MAX_SIGNATURE_LEN) {
            sign = sign.substring(0, MAX_SIGNATURE_LEN);
        }
        boolean wrapped = sign.length() >= 2 && sign.startsWith("【") && sign.endsWith("】");
        if (!wrapped) {
            // 缺方括号（或只带一侧）时补齐，先剥离已有方括号避免重复包裹
            sign = "【" + sign.replace("【", "").replace("】", "") + "】";
        }
        return sign + content;
    }

    /** 待发短信草稿（号码已归一、内容已拼接签名）。 */
    public record SmsDraft(List<String> mobiles, String content) {
    }
}
