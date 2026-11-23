package com.acme.scaffold.system.form.domain.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 表单定义版本管理纯函数规则（E8 低代码深化）：版本号分配、可发布/可回滚判定、版本间 Schema 变更摘要。
 *
 * <p>无副作用、不触碰数据库与 Spring 上下文，供 {@code FormVersionService} 在落库前统一裁决，
 * 与 {@link FormSchemaRules} 同范式，保证「什么能发布、能否回滚、改了什么」三处口径同源。</p>
 */
public final class FormVersionRules {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**  Schema 顶层被追踪的元数据字段（变更摘要用）。 */
    private static final Set<String> TRACKED_META = Set.of("version", "title");

    private FormVersionRules() {
    }

    /** 下一个业务版本号：空历史返回 1，否则取最大值 +1（溢出防护）。 */
    public static int nextVersionNo(List<Integer> existingVersionNos) {
        if (existingVersionNos == null || existingVersionNos.isEmpty()) {
            return 1;
        }
        int max = existingVersionNos.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        return max >= Integer.MAX_VALUE ? max : max + 1;
    }

    /** 是否允许回滚到目标版本：目标版本存在且不是当前已发布版本。 */
    public static boolean canRollback(Integer currentVersionNo, Integer targetVersionNo) {
        return currentVersionNo != null && targetVersionNo != null && !currentVersionNo.equals(targetVersionNo);
    }

    /**
     * 生成两个 Schema 之间的字段级变更摘要（新增/删除/修改的字段键）。
     * 仅比较 {@code fields[].key} 集合，不对字段内部属性做深比较（属性变更由调用方按 schema 原文展示）。
     */
    public static List<String> diffFields(String fromSchema, String toSchema) {
        List<String> changes = new ArrayList<>();
        Set<String> fromKeys = fieldKeys(fromSchema);
        Set<String> toKeys = fieldKeys(toSchema);
        for (String key : fromKeys) {
            if (!toKeys.contains(key)) {
                changes.add("-" + key);
            }
        }
        for (String key : toKeys) {
            if (!fromKeys.contains(key)) {
                changes.add("+" + key);
            }
        }
        return List.copyOf(changes);
    }

    /** 两个 Schema 是否完全相同（规范化 JSON 后逐字段比较：元数据 + 字段键集合）。 */
    public static boolean sameSchema(String a, String b) {
        JsonNode ra = parse(a);
        JsonNode rb = parse(b);
        if (ra == null || rb == null) {
            return Objects.equals(a, b);
        }
        for (String meta : TRACKED_META) {
            if (!Objects.equals(ra.path(meta).asText(), rb.path(meta).asText())) {
                return false;
            }
        }
        return fieldKeys(a).equals(fieldKeys(b));
    }

    private static Set<String> fieldKeys(String schema) {
        JsonNode root = parse(schema);
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (root == null || !root.path("fields").isArray()) {
            return keys;
        }
        for (JsonNode field : root.path("fields")) {
            String key = field.path("key").asText(null);
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static JsonNode parse(String schema) {
        if (schema == null || schema.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            return null;
        }
    }
}
