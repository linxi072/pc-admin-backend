package com.acme.scaffold.jooq;

import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.api.SortItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.Field;
import org.jooq.OrderField;

/**
 * 服务端排序：把客户端传入的字段名经白名单映射为数据库列，再转成 jOOQ 的 {@link OrderField}。
 *
 * <p>存在意义：{@code PageQuery.sorts} 若直接落到 SQL，等同于把列名交给客户端拼装，
 * 既可能拼出不存在/敏感列导致 500，也可能被用作 ORDER BY 注入。这里用「字段名 → 列名」的
 * 静态白名单做收口，白名单外的字段一律忽略（不报错，避免前端默认列把接口打挂）。
 */
public final class JooqSorts {

    /** 单次查询允许的最大排序字段数，防止客户端塞入超长 ORDER BY。 */
    public static final int MAX_SORT_FIELDS = 3;

    private JooqSorts() {
    }

    /**
     * 构造排序白名单（保持声明顺序，便于排查）。
     *
     * @param pairs 交替传入「客户端字段名, 数据库列名」，长度必须为偶数
     */
    public static Map<String, String> whitelist(String... pairs) {
        if (pairs == null || pairs.length % 2 != 0) {
            throw new IllegalArgumentException("排序白名单需按 字段名,列名 成对传入");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }

    /**
     * 解析排序条件；无可用的客户端排序时回落 {@code defaults}。
     *
     * <p>处理规则：
     * <ul>
     *   <li>不在白名单、表上不存在该列、或重复出现的字段直接跳过；</li>
     *   <li>最多取 {@link #MAX_SORT_FIELDS} 个字段；</li>
     *   <li>末位追加 {@code id} 兜底排序（未参与时），避免并列时分页翻页出现重复/漏行。</li>
     * </ul>
     */
    public static OrderField<?>[] resolve(JooqTables.TableRef table, PageQuery query,
                                          Map<String, String> whitelist, OrderField<?>... defaults) {
        List<OrderField<?>> resolved = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        if (query != null && query.sorts() != null) {
            for (SortItem item : query.sorts()) {
                if (item == null || item.field() == null || item.field().isBlank()) {
                    continue;
                }
                String column = whitelist.get(item.field().trim());
                if (column == null || !table.has(column) || !used.add(column)) {
                    continue;
                }
                Field<Object> field = table.field(column, Object.class);
                resolved.add(item.ascending() ? field.asc() : field.desc());
                if (resolved.size() >= MAX_SORT_FIELDS) {
                    break;
                }
            }
        }
        if (resolved.isEmpty()) {
            return defaults == null ? new OrderField<?>[0] : defaults;
        }
        // 稳定兜底：并列时按 id 倒序，保证翻页结果稳定
        if (!used.contains("id") && table.has("id")) {
            resolved.add(table.field("id", Object.class).desc());
        }
        return resolved.toArray(new OrderField<?>[0]);
    }
}
