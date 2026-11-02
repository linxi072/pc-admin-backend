package com.acme.scaffold.common.text;

/**
 * 通用文本工具：跨模块复用的小纯函数集合。
 *
 * <p>{@link #truncate(String, int)} 原为 {@code IdempotencyRecordRules}（common）、
 * {@code AlertInboxRules}（monitor）、{@code SmsRecordRules} / {@code NotificationRecordRules}
 * （system）四处完全一致的私有实现，统一收敛到此处作为单一事实来源。</p>
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * 通用截断：null 原样返回；超长截取前 {@code max} 字符（不追加省略号，用于定宽列与筛选值）。
     *
     * @param text 原文本（可空）
     * @param max  最大长度（须 &gt; 0；&le; 0 视为空串）
     * @return 截断后文本；{@code text} 为 null 时返回 null
     */
    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        if (max <= 0) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
