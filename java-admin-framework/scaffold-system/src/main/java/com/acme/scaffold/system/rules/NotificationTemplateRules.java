package com.acme.scaffold.system.rules;

import com.acme.scaffold.common.notification.Channel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知模板纯函数规则：渠道归一、变量提取、渲染、业务校验。
 *
 * <p>无副作用、不依赖 Spring 上下文，可直接离线单测；供
 * {@link com.acme.scaffold.system.service.NotificationTemplateService} 与
 * 视图层复用，保证「解析什么」与「校验什么」口径同源。</p>
 */
public final class NotificationTemplateRules {

    /** 模板编码最大长度（与迁移列宽一致）。 */
    public static final int MAX_CODE_LEN = 64;

    /** 模板名称最大长度。 */
    public static final int MAX_NAME_LEN = 100;

    /** 标题最大长度（短信忽略）。 */
    public static final int MAX_TITLE_LEN = 200;

    /** 正文最大长度（TEXT 列，留足富文本空间）。 */
    public static final int MAX_CONTENT_LEN = 4000;

    /** {@code ${name}} 占位：变量名以字母或下划线开头，允许字母/数字/下划线，变量名前后可含空白。 */
    private static final Pattern VAR_PATTERN =
            Pattern.compile("\\$\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}");

    /** 模板编码：大写字母开头，仅含大写字母/数字/下划线（与权限码/菜单码风格一致）。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private NotificationTemplateRules() {
    }

    /**
     * 渠道归一：大小写不敏感匹配 {@link Channel} 枚举，非法返回 {@code null}。
     */
    public static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String c = channel.trim().toUpperCase();
        for (Channel value : Channel.values()) {
            if (value.name().equals(c)) {
                return c;
            }
        }
        return null;
    }

    /** 渠道是否合法（对应 {@link #normalizeChannel} 非空）。 */
    public static boolean isValidChannel(String channel) {
        return normalizeChannel(channel) != null;
    }

    /**
     * 提取正文中 {@code ${var}} 变量名（保序、去重）。无内容返回空列表。
     */
    public static List<String> extractVariables(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher m = VAR_PATTERN.matcher(content);
        while (m.find()) {
            String name = m.group(1);
            if (!out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /** 变量个数（去重后）。 */
    public static int countVariables(String content) {
        return extractVariables(content).size();
    }

    /**
     * 渲染：将 {@code ${name}} 替换为 {@code vars} 中提供的取值；未提供的变量保持 {@code ${name}} 原样
     * （预览安全，不抛错）。
     *
     * <p>替换值中的 {@code $} 与 {@code \} 均按字面量处理（{@link Matcher#quoteReplacement}），
     * 避免正则注入或分组错乱。</p>
     */
    public static String render(String content, Map<String, String> vars) {
        if (content == null) {
            return "";
        }
        if (vars == null || vars.isEmpty()) {
            return content;
        }
        Matcher m = VAR_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String v = vars.get(name);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 业务校验：返回错误信息列表（空 = 通过）。
     * 仅做模板自身结构性校验；唯一性（{@code template_code}）由 Service 查库判定。
     */
    public static List<String> validate(String code, String name, String channel, String content) {
        List<String> errors = new ArrayList<>();
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            errors.add("模板编码须以大写字母开头，仅含大写字母/数字/下划线，长度 2-64");
        }
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LEN) {
            errors.add("模板名称必填且不超过 " + MAX_NAME_LEN + " 字符");
        }
        if (!isValidChannel(channel)) {
            errors.add("渠道不合法，仅支持 " + Arrays.toString(Channel.values()));
        }
        if (content == null || content.isBlank()) {
            errors.add("模板内容必填");
        } else if (content.length() > MAX_CONTENT_LEN) {
            errors.add("模板内容不超过 " + MAX_CONTENT_LEN + " 字符");
        }
        return errors;
    }
}
