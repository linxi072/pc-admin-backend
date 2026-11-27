package com.acme.scaffold.codegen.service;

import java.util.Locale;

/**
 * 代码生成模板文本净化：把不可信文本收敛为注释 / 字符串 / HTML 文本都安全的形态。
 *
 * <p>生成器输入（功能名、作者、表/列注释）来自管理端请求或数据库元信息，会被同时拼入
 * Java 块注释、Java 字符串字面量、Vue 注释 / 属性 / 文本、JS 行注释等不同上下文。
 * 这里统一把上下文 terminates 字符与模板插值字符替换为全角等价字符：
 * 既保留中文可读性，又保证单份净化结果可安全用于所有上下文，且重复净化幂等。</p>
 */
public final class TemplateEscapes {

    private TemplateEscapes() {
    }

    /**
     * 通用安全文本：压平换行/制表符，替换会闭合注释、跳出属性/字符串或触发模板插值的关键字符。
     *
     * @return 可安全拼入 Java 注释 / 字符串、Vue 注释 / 属性 / 文本、JS 注释的文本；null 返回空串
     */
    public static String safeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replace("\u2028", " ")
                .replace("\u2029", " ")
                .replace("*/", "* /")
                .replace("\\", "＼")
                .replace("\"", "“")
                .replace("'", "’")
                .replace("<", "‹")
                .replace(">", "›")
                .replace("&", "＆")
                .replace("{", "｛")
                .replace("}", "｝");
    }

    /**
     * 表名 → {@code SqlTables} 引用安全标识符：仅保留大写字母/数字/下划线，非法字符归一为下划线。
     */
    public static String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "TABLE";
        }
        String s = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (s.isEmpty()) {
            return "TABLE";
        }
        return Character.isDigit(s.charAt(0)) ? "T_" + s : s;
    }

    /**
     * 类名安全校验：类名会进入 zip 条目路径与 Java 标识符，禁止任何路径分隔符或非法字符。
     *
     * @throws IllegalArgumentException 类名不符合 Java 标识符规范时
     */
    public static String safeClassName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        String cls = value.trim();
        if (!cls.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("className 仅允许字母/数字/下划线且以字母开头: " + cls);
        }
        return cls;
    }
}
