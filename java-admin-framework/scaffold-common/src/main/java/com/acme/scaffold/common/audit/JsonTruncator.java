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
 *   <li>原样保留：长度未超上限直接返回；</li>
 *   <li>紧凑化 + 结构化裁剪：超长但合法 JSON，先去除空白压缩；仍超则对对象/数组递归丢弃
 *       低价值字段（空值字段、数组末位元素）直至满足上限；</li>
 *   <li>文本兜底：原始非合法 JSON 或结构化裁剪失败，截断到上限并包装为 JSON 字符串标量
 *       （转义内部引号，保证列类型不被破坏）。</li>
 * </ol>
 */
public final class JsonTruncator {

    private JsonTruncator() {
    }

    /**
     * 安全截断。
     *
     * @param s      原始摘要（可为 null）
     * @param max    最大字符数（含）
     * @param mapper 调用方注入的 {@link ObjectMapper}（避免本工具自行持有 Spring 上下文，便于单测）
     * @return 合法 JSON 且长度 ≤ max 的字符串；入参为 null 时返回 null
     */
    public static String safeTruncate(String s, int max, ObjectMapper mapper) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
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
        String cut = s.substring(0, max);
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

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
