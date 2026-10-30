package com.acme.scaffold.common.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 导入字段校验构件（纯函数，零依赖）。
 *
 * <p>每个方法返回<b>错误信息</b>（{@code null} 表示校验通过），便于在 {@link ImportTask#validate(ImportRow)}
 * 中直接组合：</p>
 *
 * <pre>{@code
 * return ImportValidators.collect(
 *         ImportValidators.required(row.trimmed("字典类型"), "字典类型"),
 *         ImportValidators.maxLength(row.value("字典类型"), "字典类型", 64));
 * }</pre>
 *
 * <p>统一如此设计的原因：导入场景需要<b>一次收集全部字段错误</b>后一并回传（用户改一次就能全部修正），
 * 而非常见的「抛首个异常即返回」。</p>
 *
 * <p>本类不依赖 Spring 容器，便于离线单测。</p>
 */
public final class ImportValidators {

    private ImportValidators() {
    }

    /** 把若干「可能为空」的校验结果收拢为非空错误列表（空列表表示全部通过）。 */
    public static List<String> collect(String... results) {
        List<String> errors = new ArrayList<>();
        if (results != null) {
            for (String r : results) {
                if (r != null && !r.isBlank()) {
                    errors.add(r);
                }
            }
        }
        return List.copyOf(errors);
    }

    /** 必填校验：空白（含 null）返回错误。 */
    public static String required(String value, String field) {
        return (value == null || value.isBlank()) ? "「" + field + "」不能为空" : null;
    }

    /** 最大长度校验（按字符数，null / 空白视为通过，需与其他规则组合使用）。 */
    public static String maxLength(String value, String field, int max) {
        if (value == null || value.length() <= max) {
            return null;
        }
        return "「" + field + "」长度不能超过 " + max + " 个字符（当前 " + value.length() + " 个）";
    }

    /**
     * 整数范围校验。
     *
     * @param optional 为 true 时空值视为通过；为 false 时空值报「不能为空」
     * @param min      允许的最小值（含）
     * @param max      允许的最大值（含）
     */
    public static String integerRange(String value, String field, long min, long max, boolean optional) {
        if (value == null || value.isBlank()) {
            return optional ? null : "「" + field + "」不能为空";
        }
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return "「" + field + "」必须为整数";
        }
        if (parsed < min || parsed > max) {
            return "「" + field + "」必须在 " + min + " ~ " + max + " 之间";
        }
        return null;
    }

    /**
     * 枚举取值校验（忽略大小写）。
     *
     * @param optional 为 true 时空值视为通过
     * @param allowed  允许的取值集合（按传入顺序展示于错误信息中）
     */
    public static String oneOf(String value, String field, boolean optional, String... allowed) {
        if (value == null || value.isBlank()) {
            return optional ? null : "「" + field + "」不能为空";
        }
        String v = value.trim();
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) {
                return null;
            }
        }
        return "「" + field + "」取值必须为 " + String.join(" / ", allowed);
    }

    /**
     * 正则校验（整体匹配）。
     *
     * @param optional 为 true 时空值视为通过
     * @param hint     不匹配时的补充说明（可为 null，此时只提示格式不合法）
     */
    public static String matches(String value, String field, Pattern pattern, boolean optional, String hint) {
        if (value == null || value.isBlank()) {
            return optional ? null : "「" + field + "」不能为空";
        }
        if (pattern.matcher(value.trim()).matches()) {
            return null;
        }
        return "「" + field + "」格式不合法" + (hint == null || hint.isBlank() ? "" : "（" + hint + "）");
    }
}
