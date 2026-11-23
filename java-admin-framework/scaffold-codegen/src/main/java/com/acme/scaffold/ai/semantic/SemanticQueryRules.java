package com.acme.scaffold.ai.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 语义查询解析规则（E9，P3 探索）：自然语言 → 结构化查询意图（离线启发式，零外部依赖）。
 *
 * <p>这是「语义查询」的可落地最小闭环：不调用大模型、不新增依赖，用确定性规则把中文/英文
 * 查询描述归一为 {@link SemanticQuery}（实体、精确/模糊筛选、排序字段与方向、分页）。
 * 上层可用该意图驱动 MyBatis DSL 查询；当未来接入真实 LLM 时，替换解析器即可，意图契约不变。</p>
 *
 * <p><b>设计约束</b>：只做「意图提取」，不拼 SQL、不拼接用户输入到 SQL（避免注入）；
 * 未知描述回退为「查全部 + 默认分页」，不抛异常（fail-open 到最保守查询）。</p>
 */
public final class SemanticQueryRules {

    private static final Set<String> ENTITY_HINTS = Set.of(
            "用户", "user", "角色", "role", "菜单", "menu", "部门", "dept", "岗位", "post",
            "文件", "file", "日志", "log", "通知", "notice", "表单", "form", "报表", "report");
    private static final Set<String> DESC_HINTS = Set.of("降序", "倒序", "最新", "最近", "desc", "newest");
    private static final Set<String> ASC_HINTS = Set.of("升序", "正序", "asc", "oldest");
    private static final Set<String> TIME_FIELDS = Set.of("时间", "日期", "创建", "更新", "time", "date", "created", "updated");

    private SemanticQueryRules() {
    }

    /** 语义查询意图。 */
    public record SemanticQuery(
            String entity,
            List<Filter> filters,
            String orderBy,
            String direction,
            int page,
            int size) {

        public SemanticQuery {
            filters = filters == null ? List.of() : List.copyOf(filters);
        }
    }

    /** 单个筛选条件（field 为归一化字段名，value 为提取值）。 */
    public record Filter(String field, Operator operator, String value) {
    }

    public enum Operator {
        EQ,
        LIKE
    }

    /** 解析自然语言描述为查询意图。 */
    public static SemanticQuery parse(String text) {
        String raw = text == null ? "" : text.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String entity = detectEntity(lower);
        List<Filter> filters = extractFilters(raw);
        String orderBy = detectOrderField(raw);
        String direction = detectDirection(lower);
        return new SemanticQuery(entity, filters, orderBy, direction, 1, 20);
    }

    /** 从「字段名=值」「字段名:值」形式提取精确/模糊筛选（支持中文冒号）。 */
    public static List<Filter> extractFilters(String text) {
        List<Filter> filters = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return filters;
        }
        String[] tokens = text.split("[\\s,，;；]+");
        for (String token : tokens) {
            int sep = indexOfSeparator(token);
            if (sep <= 0 || sep >= token.length() - 1) {
                continue;
            }
            String field = token.substring(0, sep).trim();
            String value = token.substring(sep + 1).trim();
            if (field.isEmpty() || value.isEmpty()) {
                continue;
            }
            Operator op = value.contains("%") || value.contains("*") ? Operator.LIKE : Operator.EQ;
            filters.add(new Filter(normalizeField(field), op, value.replaceAll("[%*]", "")));
        }
        return List.copyOf(filters);
    }

    /** 字段归一：含时间/日期语义映射为 createdAt，其余保留原文小写（上层再做列白名单映射）。 */
    public static String normalizeField(String field) {
        if (field == null || field.isBlank()) {
            return "";
        }
        String f = field.trim().toLowerCase(Locale.ROOT);
        for (String hint : TIME_FIELDS) {
            if (f.contains(hint)) {
                return "createdAt";
            }
        }
        return f;
    }

    /** 对意图做安全校验：分页范围钳制，防止超大 size 拖垮查询。 */
    public static SemanticQuery sanitize(SemanticQuery query) {
        if (query == null) {
            return new SemanticQuery("", List.of(), null, "ASC", 1, 20);
        }
        int page = Math.max(1, query.page());
        int size = Math.max(1, Math.min(200, query.size()));
        String direction = "DESC".equalsIgnoreCase(query.direction()) ? "DESC" : "ASC";
        return new SemanticQuery(query.entity(), query.filters(), query.orderBy(), direction, page, size);
    }

    private static String detectEntity(String lower) {
        for (String hint : ENTITY_HINTS) {
            if (lower.contains(hint)) {
                return hint;
            }
        }
        return "";
    }

    private static String detectOrderField(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String hint : TIME_FIELDS) {
            if (lower.contains(hint)) {
                return "createdAt";
            }
        }
        return "";
    }

    private static String detectDirection(String lower) {
        if (DESC_HINTS.stream().anyMatch(lower::contains)) {
            return "DESC";
        }
        if (ASC_HINTS.stream().anyMatch(lower::contains)) {
            return "ASC";
        }
        return "ASC";
    }

    private static int indexOfSeparator(String token) {
        int eq = token.indexOf('=');
        int colon = token.indexOf(':');
        int cnColon = token.indexOf('：');
        int sep = -1;
        for (int candidate : new int[]{eq, colon, cnColon}) {
            if (candidate >= 0 && (sep < 0 || candidate < sep)) {
                sep = candidate;
            }
        }
        return sep;
    }
}
