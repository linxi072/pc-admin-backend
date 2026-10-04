package com.acme.scaffold.jooq;

import com.acme.scaffold.common.IdGenerator;
import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.api.PageResult;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.impl.DSL;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * jOOQ 读写辅助层，替代 MyBatis-Plus 的 BaseMapper / Wrapper / 分页插件 / MetaObjectHandler。
 * <ul>
 *   <li>写入：POJO（camelCase）→ 列（snake_case），自动填充 id / tenant_id / deleted / version / 时间字段；</li>
 *   <li>读取：Record → POJO 由 {@link SnakeRecordMapper} 完成；</li>
 *   <li>逻辑删除：{@link #notDeleted} 谓词 + {@link #delete} 的 logical 开关；</li>
 *   <li>null 字段不写入（沿用 MyBatis-Plus 的 NOT_NULL 字段策略），交由数据库默认值处理。</li>
 * </ul>
 */
public final class JooqWriters {

    private JooqWriters() {
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /** 插入实体，自动填充 id / tenant_id / deleted / version / created_at / updated_at。 */
    public static <T> void insert(DSLContext dsl, JooqTables.TableRef table, T pojo) {
        Map<String, Object> values = columnValues(pojo, table);
        // 主键回填：与 MyBatis-Plus 的 ASSIGN_ID 行为一致，insert 后 POJO 可拿到 id
        Long id = IdGenerator.nextId();
        setIdIfPresent(pojo, id);
        putIfPresent(values, table, "id", id);
        putIfPresent(values, table, "tenant_id", 0L);
        putIfPresent(values, table, "deleted", 0);
        putIfPresent(values, table, "version", 0);
        putIfPresent(values, table, "created_at", Timestamp.valueOf(LocalDateTime.now()));
        putIfPresent(values, table, "updated_at", Timestamp.valueOf(LocalDateTime.now()));
        if (values.isEmpty()) {
            return;
        }
        dsl.insertInto(table.table()).set(toFieldMap(table, values)).execute();
    }

    /** 按 id 选择性更新（跳过 null 字段），自动刷新 updated_at。 */
    public static <T> void updateById(DSLContext dsl, JooqTables.TableRef table, Long id, T pojo) {
        Map<String, Object> values = columnValues(pojo, table);
        putIfPresent(values, table, "updated_at", Timestamp.valueOf(LocalDateTime.now()));
        if (values.isEmpty()) {
            return;
        }
        dsl.update(table.table())
                .set(toFieldMap(table, values))
                .where(table.field("id", Long.class).eq(id))
                .execute();
    }

    /**
     * 按 id 删除。logical=true 且表含 deleted 列时走逻辑删除（deleted=1），否则物理删除。
     * （sys_api_resource 等无 deleted 列的表保持物理删除语义，与迁移前一致。）
     */
    public static void delete(DSLContext dsl, JooqTables.TableRef table, Long id, boolean logical) {
        if (logical && table.has("deleted")) {
            dsl.update(table.table())
                    .set(table.field("deleted", Integer.class), 1)
                    .set(table.field("updated_at", Timestamp.class), Timestamp.valueOf(LocalDateTime.now()))
                    .where(table.field("id", Long.class).eq(id))
                    .execute();
        } else {
            dsl.deleteFrom(table.table())
                    .where(table.field("id", Long.class).eq(id))
                    .execute();
        }
    }

    /** 按单列等值删除（用于关联表的重建式赋值）。按值类型选择字段类型，保证参数绑定正确。 */
    public static void deleteByColumn(DSLContext dsl, JooqTables.TableRef table, String column, Object value) {
        if (value == null || !table.has(column)) {
            return;
        }
        if (value instanceof Long lv) {
            dsl.deleteFrom(table.table()).where(table.field(column, Long.class).eq(lv)).execute();
        } else if (value instanceof String sv) {
            dsl.deleteFrom(table.table()).where(table.field(column, String.class).eq(sv)).execute();
        } else {
            dsl.deleteFrom(table.table()).where(table.field(column, Object.class).eq(value)).execute();
        }
    }

    // ------------------------------------------------------------------
    // 读取：替代 selectOne / selectById / selectList / selectCount / selectPage
    // ------------------------------------------------------------------

    /** 逻辑删除谓词：表含 deleted 列时追加 deleted = 0；否则返回恒真条件。 */
    public static Condition notDeleted(JooqTables.TableRef table) {
        return table.has("deleted")
                ? table.field("deleted", Integer.class).eq(0)
                : DSL.trueCondition();
    }

    /** 按条件查询单条记录并映射为 POJO；无结果返回 null。 */
    public static <E> E fetchOne(DSLContext dsl, JooqTables.TableRef table, Class<E> type, Condition cond) {
        return dsl.selectFrom(table.table()).where(cond).fetchOne(new SnakeRecordMapper<>(type));
    }

    /** 按 id 查询单条记录（含逻辑删除过滤）。 */
    public static <E> E fetchById(DSLContext dsl, JooqTables.TableRef table, Class<E> type, Long id) {
        if (!table.has("id")) {
            return null;
        }
        return fetchOne(dsl, table, type,
                DSL.and(notDeleted(table), table.field("id", Long.class).eq(id)));
    }

    /** 按条件查询列表。 */
    public static <E> List<E> fetchList(DSLContext dsl, JooqTables.TableRef table, Class<E> type,
                                        Condition cond) {
        return dsl.selectFrom(table.table()).where(cond).fetch(new SnakeRecordMapper<>(type));
    }

    /** 按条件查询列表并排序。 */
    public static <E> List<E> fetchList(DSLContext dsl, JooqTables.TableRef table, Class<E> type,
                                        Condition cond, OrderField<?>... orderBy) {
        if (orderBy == null || orderBy.length == 0) {
            return fetchList(dsl, table, type, cond);
        }
        return dsl.selectFrom(table.table()).where(cond).orderBy(orderBy)
                .fetch(new SnakeRecordMapper<>(type));
    }

    /** 按条件统计数量。 */
    public static long count(DSLContext dsl, JooqTables.TableRef table, Condition cond) {
        Integer total = dsl.selectCount().from(table.table()).where(cond).fetchOne(0, int.class);
        return total == null ? 0L : total.longValue();
    }

    /** 分页查询（count + limit/offset），等价于 MyBatis-Plus 的 selectPage。 */
    public static <E> PageResult<E> page(DSLContext dsl, JooqTables.TableRef table, Class<E> type,
                                         Condition cond, PageQuery q, OrderField<?>... orderBy) {
        long total = count(dsl, table, cond);
        var step = dsl.selectFrom(table.table()).where(cond);
        List<E> records;
        if (orderBy != null && orderBy.length > 0) {
            records = step.orderBy(orderBy)
                    .limit(q.size()).offset((q.page() - 1) * q.size())
                    .fetch(new SnakeRecordMapper<>(type));
        } else {
            records = step.limit(q.size()).offset((q.page() - 1) * q.size())
                    .fetch(new SnakeRecordMapper<>(type));
        }
        return new PageResult<>(q.page(), q.size(), total, records);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 按列名收集 POJO 非 null 属性（跳过 id，由主键生成器统一处理）。 */
    private static <T> Map<String, Object> columnValues(T pojo, JooqTables.TableRef table) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(pojo.getClass()).getPropertyDescriptors()) {
                String prop = pd.getName();
                if ("class".equals(prop) || "id".equals(prop)) {
                    continue;
                }
                Method getter = pd.getReadMethod();
                if (getter == null) {
                    continue;
                }
                Object value = getter.invoke(pojo);
                // NOT_NULL 策略：null 不进入 SQL，交由数据库默认值处理
                if (value == null) {
                    continue;
                }
                String column = SnakeRecordMapper.toSnake(prop);
                if (!table.has(column)) {
                    continue;
                }
                map.put(column, normalize(value));
            }
        } catch (ReflectiveOperationException | IntrospectionException e) {
            throw new IllegalStateException("读取 POJO 属性失败: " + pojo.getClass().getName(), e);
        }
        return map;
    }

    /** 列名 → 类型化字段。字段类型取值的运行时类型，保证 JDBC 绑定正确。 */
    private static Map<Field<?>, Object> toFieldMap(JooqTables.TableRef table, Map<String, Object> byColumn) {
        Map<Field<?>, Object> map = new LinkedHashMap<>();
        byColumn.forEach((column, value) -> {
            Class<?> type = (value == null) ? Object.class : value.getClass();
            map.put(table.field(column, type), value);
        });
        return map;
    }

    /** 自动填充：列存在且 POJO 未显式赋值（或值为 null）时才填入。 */
    private static void putIfPresent(Map<String, Object> byColumn, JooqTables.TableRef table,
                                     String column, Object value) {
        if (!table.has(column)) {
            return;
        }
        if (byColumn.containsKey(column) && byColumn.get(column) != null) {
            return;
        }
        byColumn.put(column, normalize(value));
    }

    /** 将生成的主键回填到 POJO（存在 setId(Long) 时）。复合主键关联表无此方法，静默跳过。 */
    private static void setIdIfPresent(Object pojo, Long id) {
        if (id == null) {
            return;
        }
        try {
            Method setter = pojo.getClass().getMethod("setId", Long.class);
            setter.invoke(pojo, id);
        } catch (NoSuchMethodException ignored) {
            // 无主键的关联表，忽略
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("回填主键失败: " + pojo.getClass().getName(), e);
        }
    }

    /** Java 时间类型 → JDBC 类型，便于动态 DSL 正确绑定。 */
    private static Object normalize(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (value instanceof LocalDate ld) {
            return Date.valueOf(ld);
        }
        return value;
    }
}
