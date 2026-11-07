package com.acme.scaffold.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 审计摘要安全截断器（F4 修复）。
 *
 * <p>审计两摘要列（request_summary / result_summary）为 JSON 类型，截断不得破坏 JSON 语法，
 * 否则 MySQL 以 3140 拒绝写入、摘要整段丢失。本工具保证：<b>任意超长输入，输出必为合法 JSON
 * 且长度 ≤ max</b>（当原始为合法 JSON 时尽量保留原文；原始非法时退化为安全字符串标量）。</p>
 *
 * <p>三级降级：
 * <ol>
 *   <li>原样保留：长度未超上限且为合法 JSON 时直接返回（不破坏既有契约）；
 *       长度未超上限但为非法 JSON 时退化为 JSON 字符串标量，保证列类型不被破坏；</li>
 *   <li>紧凑化 + 结构化裁剪：超长但合法 JSON，先去除空白压缩；仍超则对对象/数组递归丢弃
 *       低价值字段（空值字段、数组末位元素）直至满足上限；</li>
 *   <li>文本兜底：原始非合法 JSON 或结构化裁剪失败，截断到上限并包装为 JSON 字符串标量
 *       （转义内部引号、反斜杠与全部 C0 控制字符，保证列类型与 JSON 语法均不被破坏）。</li>
 * </ol>
 *
 * <p>max 过小（< {@value #MIN_LENGTH}）时归一到下界：不存在更短的合法 JSON，
 * 此时优先保证「输出必为合法 JSON」，而非字面的长度上限。</p>
 */
public final class JsonTruncator {

    /**
     * 可承载「合法 JSON 字符串标量」的最小长度：{@code ""} 即 2 个字符。
     *
     * <p>max 小于该值时，「输出为合法 JSON」与「长度 ≤ max」两个要求不可能同时满足，
     * 此时以「合法 JSON」优先，内部把 max 归一到本下界（返回 {@code ""}）。</p>
     */
    public static final int MIN_LENGTH = 2;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JsonTruncator() {
    }

    /**
     * 安全截断。
     *
     * @param s      原始摘要（可为 null）
     * @param maxArg 最大字符数（含）；< {@link #MIN_LENGTH} 时内部归一到 {@link #MIN_LENGTH}
     * @param mapper 调用方注入的 {@link ObjectMapper}（避免本工具自行持有 Spring 上下文，便于单测）
     * @return 合法 JSON 且长度 ≤ max 的字符串；入参为 null 时返回 null
     */
    public static String safeTruncate(String s, int maxArg, ObjectMapper mapper) {
        if (s == null) {
            return null;
        }
        final int max = Math.max(maxArg, MIN_LENGTH);
        if (s.length() <= max) {
            // 短输入同样必须校验 JSON 合法性：短小但非法的 JSON（如被截断的 "{"）同样会被
            // MySQL 3140 拒绝写入，原样返回会整段丢失。合法则原样保留（不破坏既有契约），
            // 非法则退化为 JSON 字符串标量（必合法），保证输出恒为合法 JSON。
            try {
                mapper.readTree(s);
                return s;
            } catch (Exception ignored) {
                return truncateText(s, max);
            }
        }
        // 1) 尝试紧凑化（去空白），多数日志 JSON 含大量空格，压缩后常可直接满足上限
        try {
            JsonNode node = mapper.readTree(s);
            String compact = mapper.writeValueAsString(node);
            if (compact.length() <= max) {
                return compact;
            }
            // 2) 结构化裁剪：对象丢字段 / 数组丢末位，直至满足上限
            String trimmed = trimNode(node, mapper, max);
            if (trimmed != null && trimmed.length() <= max) {
                return trimmed;
            }
        } catch (Exception ignored) {
            // 非合法 JSON：进入文本兜底
        }
        // 3) 文本兜底：截断并包装为 JSON 字符串标量（必合法）
        return truncateText(s, max);
    }

    /** 对合法 JSON 节点做结构化裁剪：数组从末位丢弃元素，对象先丢空值字段再逐字段丢弃。 */
    private static String trimNode(JsonNode node, ObjectMapper mapper, int max) {
        try {
            if (node.isArray()) {
                List<JsonNode> snapshot = new ArrayList<>();
                node.forEach(snapshot::add);
                for (int i = snapshot.size() - 1; i >= 0; i--) {
                    ArrayNode candidate = mapper.createArrayNode();
                    for (int j = 0; j <= i; j++) {
                        candidate.add(snapshot.get(j));
                    }
                    String json = mapper.writeValueAsString(candidate);
                    if (json.length() <= max) {
                        return json;
                    }
                }
                return null; // 即使只留一个元素仍超限，交由文本兜底
            }
            if (node.isObject()) {
                ObjectNode copy = (ObjectNode) node;
                dropEmptyFields(copy);
                String json = mapper.writeValueAsString(copy);
                if (json.length() <= max) {
                    return json;
                }
                List<String> names = new ArrayList<>();
                copy.fieldNames().forEachRemaining(names::add);
                for (int i = names.size() - 1; i >= 0; i--) {
                    copy.remove(names.get(i));
                    json = mapper.writeValueAsString(copy);
                    if (json.length() <= max) {
                        return json;
                    }
                }
                return null;
            }
            // 标量（字符串/数字/布尔/null）：无法结构化裁剪，交由文本兜底
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 丢弃空值/空容器字段，优先削减体积。 */
    private static void dropEmptyFields(ObjectNode obj) {
        List<String> toRemove = new ArrayList<>();
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            JsonNode child = obj.get(name);
            if (child.isNull()
                    || (child.isTextual() && child.asText().isEmpty())
                    || (child.isArray() && child.isEmpty())
                    || (child.isObject() && child.isEmpty())) {
                toRemove.add(name);
            }
        }
        toRemove.forEach(obj::remove);
    }

    /**
     * 文本兜底：截断到 max 字符，包装为 JSON 字符串标量（转义内部双引号与反斜杠）。
     * 通过对内文预算做转义最坏翻倍留量 + 兜底循环，严格保证结果长度 ≤ max。
     */
    private static String truncateText(String s, int max) {
        // 入参长度可能 ≤ max（短非法 JSON 兜底分支会传入 max 远大于实际长度），
        // 必须先夹紧上界，避免 StringIndexOutOfBounds。
        String cut = s.length() <= max ? s : s.substring(0, max);
        int innerBudget = (max - 2) / 2; // 转义后最坏翻倍，留足余量
        if (innerBudget < 0) {
            innerBudget = 0;
        }
        String inner = cut.length() > innerBudget ? cut.substring(0, innerBudget) : cut;
        String wrapped = "\"" + escapeJson(inner) + "\"";
        while (wrapped.length() > max && inner.length() > 0) {
            inner = inner.substring(0, inner.length() - 1);
            wrapped = "\"" + escapeJson(inner) + "\"";
        }
        return wrapped;
    }

    /**
     * JSON 字符串标量内文转义：除反斜杠/双引号外，还必须转义全部 C0 控制字符。
     *
     * <p>原始摘要常含换行、制表符或 {@code \u0001} 一类控制字符；仅转义 {@code \} 与 {@code "}
     * 会把裸控制字符写进 JSON 字符串，导致 MySQL 3140 仍然拒绝、退化兜底失效。</p>
     */
    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00").append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
