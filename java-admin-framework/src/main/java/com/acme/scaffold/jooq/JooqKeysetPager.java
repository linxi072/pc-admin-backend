package com.acme.scaffold.jooq;

import com.acme.scaffold.common.api.CursorCodec;
import com.acme.scaffold.common.api.KeysetPageQuery;
import com.acme.scaffold.common.api.KeysetPageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectSeekStepN;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * keyset（游标）分页引擎，替代深翻页场景下的 offset 分页（LIMIT/OFFSET 随偏移增大而劣化）。
 *
 * <p>约定：
 * <ul>
 *   <li>排序键由服务端固定（{@code keyFields} + {@code ascending}），与 orderBy 完全一致，保证 seek 正确；</li>
 *   <li>排序键须构成「唯一且确定的排序」（通常含业务时间列 + id 作为 tie-breaker）；</li>
 *   <li>游标不透明（{@link CursorCodec} 编码），客户端不可伪造排序；</li>
 *   <li>取 {@code size+1} 条判定是否还有下一页，避免 count 开销。</li>
 * </ul>
 */
public final class JooqKeysetPager {

    private JooqKeysetPager() {
    }

    /**
     * 执行 keyset 分页查询。
     *
     * @param keyExtractor 从 POJO 抽取排序键值（顺序与 keyFields 一致），用于生成下一页游标
     */
    public static <E> KeysetPageResult<E> query(
            DSLContext dsl,
            JooqTables.TableRef table,
            Class<E> type,
            Condition cond,
            KeysetPageQuery q,
            Function<E, Object[]> keyExtractor,
            Field<?>[] keyFields,
            boolean[] ascending) {

        if (keyFields.length != ascending.length) {
            throw new IllegalStateException("keyFields 与 ascending 长度不一致");
        }
        OrderField<?>[] order = new OrderField[keyFields.length];
        for (int i = 0; i < keyFields.length; i++) {
            order[i] = ascending[i] ? keyFields[i].asc() : keyFields[i].desc();
        }

        List<String> decoded = q.decodedCursor();
        if (!decoded.isEmpty() && decoded.size() != keyFields.length) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "游标字段数与排序键不一致（预期 " + keyFields.length + "，实际 " + decoded.size() + "）");
        }

        List<E> all = buildSelect(dsl, table, cond, q, keyFields, ascending)
                .fetch(new SnakeRecordMapper<>(type));
        boolean hasMore = all.size() > q.size();
        List<E> page = hasMore ? new ArrayList<>(all.subList(0, q.size())) : all;
        String nextCursor = null;
        if (hasMore) {
            E last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(Arrays.asList(keyExtractor.apply(last)));
        }
        return KeysetPageResult.of(q.size(), page, nextCursor);
    }

    /**
     * 构建 keyset 查询（含 seek + limit），不执行；供 {@link #query} 复用，亦便于离线渲染/测试。
     */
    static Select<Record> buildSelect(
            DSLContext dsl,
            JooqTables.TableRef table,
            Condition cond,
            KeysetPageQuery q,
            Field<?>[] keyFields,
            boolean[] ascending) {

        OrderField<?>[] order = new OrderField[keyFields.length];
        for (int i = 0; i < keyFields.length; i++) {
            order[i] = ascending[i] ? keyFields[i].asc() : keyFields[i].desc();
        }

        SelectSeekStepN<Record> step = dsl.selectFrom(table.table()).where(cond).orderBy(order);

        List<String> decoded = q.decodedCursor();
        if (decoded.isEmpty()) {
            return step.limit(q.size() + 1);
        }
        Object[] seekValues = new Object[keyFields.length];
        for (int i = 0; i < keyFields.length; i++) {
            seekValues[i] = convertToFieldType(keyFields[i], decoded.get(i));
        }
        return step.seek(seekValues).limit(q.size() + 1);
    }

    /** 把游标解码出的字符串按目标字段类型还原，保证 jOOQ 绑定正确。 */
    private static Object convertToFieldType(Field<?> field, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Class<?> type = field.getType();
        if (type == Long.class || type == long.class) {
            return Long.parseLong(raw);
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(raw);
        }
        if (type == Short.class || type == short.class) {
            return Short.parseShort(raw);
        }
        if (type == Double.class || type == double.class) {
            return Double.parseDouble(raw);
        }
        if (type == Float.class || type == float.class) {
            return Float.parseFloat(raw);
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(raw);
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(raw);
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(raw);
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(raw);
        }
        if (type == java.sql.Timestamp.class) {
            return java.sql.Timestamp.valueOf(LocalDateTime.parse(raw));
        }
        return raw;
    }
}
