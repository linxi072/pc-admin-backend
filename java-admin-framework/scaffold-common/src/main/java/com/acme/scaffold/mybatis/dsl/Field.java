package com.acme.scaffold.mybatis.dsl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 类型化列 / SQL 表达式。兼容原 jOOQ 动态 DSL 的常用构造方式：
 * 比较、范围、集合、空值、排序、聚合、别名与 CASE 表达式。
 */
public class Field<T> extends SqlFragment implements OrderField<T> {

    private final Class<T> type;
    private final String alias;

    public Field(String sql, Class<T> type) {
        this(sql, type, List.of(), null);
    }

    public Field(String sql, Class<T> type, List<Object> binds) {
        this(sql, type, binds, null);
    }

    @SuppressWarnings("unchecked")
    public Field(String sql, Class<?> type, List<Object> binds, String alias) {
        super(sql, binds);
        this.type = (Class<T>) type;
        this.alias = alias;
    }

    public Class<T> getType() {
        return type;
    }

    public String getDataType() {
        return type == null ? "Object" : type.getSimpleName();
    }

    public String getName() {
        return alias != null ? alias : sql;
    }

    public String alias() {
        return alias;
    }

    public String name() {
        return sql;
    }

    /** 是否为不需要额外别名的简单标识符（列名）。 */
    public boolean isSimpleIdentifier() {
        if (sql.isEmpty() || !Character.isLetterOrDigit(sql.charAt(0)) && sql.charAt(0) != '_') {
            return false;
        }
        for (char c : sql.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') {
                return false;
            }
        }
        return true;
    }

    public Field<T> as(String newAlias) {
        return new Field<>(sql, type, new ArrayList<>(binds), newAlias);
    }

    @SuppressWarnings("unchecked")
    public <X> Field<X> cast(Class<X> newType) {
        return new Field<>(sql, newType, new ArrayList<>(binds), alias);
    }

    // ---------------- 比较 ----------------

    public Condition eq(Object value) {
        return value == null ? isNull() : Condition.of(sql + " = ?", value);
    }

    public Condition eq(Field<?> other) {
        return new Condition(sql + " = " + other.sql, concat(this, other));
    }

    public Condition eq(Select<?> sub) {
        SqlStatement<?> q = (SqlStatement<?>) sub;
        String inner = q.renderSql();
        return new Condition(sql + " = (" + replacePlaceholders(inner, 0) + ")", joinBinds(q.renderBinds()));
    }

    public Condition ne(Object value) {
        return value == null ? isNotNull() : Condition.of(sql + " <> ?", value);
    }

    public Condition ne(Field<?> other) {
        return new Condition(sql + " <> " + other.sql, concat(this, other));
    }

    public Condition gt(Object value) {
        return Condition.of(sql + " > ?", value);
    }

    public Condition ge(Object value) {
        return Condition.of(sql + " >= ?", value);
    }

    public Condition lt(Object value) {
        return Condition.of(sql + " < ?", value);
    }

    public Condition le(Object value) {
        return Condition.of(sql + " <= ?", value);
    }

    public Condition greaterOrEqual(Object value) {
        return ge(value);
    }

    public Condition greaterThan(Object value) {
        return gt(value);
    }

    public Condition lessOrEqual(Object value) {
        return le(value);
    }

    public Condition equal(Object value) {
        return eq(value);
    }

    public Condition notEqual(Object value) {
        return ne(value);
    }

    public Condition lessThan(Object value) {
        return lt(value);
    }

    public Condition like(Object value) {
        return Condition.of(sql + " LIKE ?", value);
    }

    public Condition like(Field<?> other) {
        return new Condition(sql + " LIKE " + other.sql, concat(this, other));
    }

    public Condition notLike(Field<?> other) {
        return new Condition(sql + " NOT LIKE " + other.sql, concat(this, other));
    }

    /** jOOQ 风格 LIKE ... ESCAPE 'x'，用于已转义的模糊匹配。 */
    public Condition like(Object value, char escape) {
        return Condition.of(sql + " LIKE ? ESCAPE '" + escape + "'", value);
    }

    public Condition likeIgnoreCase(Object value) {
        return Condition.of("lower(" + sql + ") LIKE lower(?)", value);
    }

    public Condition likeIgnoreCase(Field<?> other) {
        return new Condition("lower(" + sql + ") LIKE lower(" + other.sql + ")", concat(this, other));
    }

    public Condition notLike(Object value) {
        return Condition.of(sql + " NOT LIKE ?", value);
    }

    public Condition isNull() {
        return Condition.of(sql + " IS NULL");
    }

    public Condition isNotNull() {
        return Condition.of(sql + " IS NOT NULL");
    }

    public Condition in(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return Condition.FALSE;
        }
        StringBuilder sb = new StringBuilder(sql).append(" IN (");
        List<Object> b = new ArrayList<>(binds);
        int i = 0;
        for (Object v : values) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append('?');
            b.add(v);
        }
        return new Condition(sb.append(')').toString(), b);
    }

    public Condition in(Object... values) {
        return in(values == null ? List.of() : Arrays.asList(values));
    }

    public Condition in(Select<?> sub) {
        SqlStatement<?> q = (SqlStatement<?>) sub;
        return new Condition(sql + " IN (" + q.renderSql() + ")", joinBinds(q.renderBinds()));
    }

    public Condition notIn(Collection<?> values) {
        Condition c = in(values);
        return new Condition(c.sql.replace(" IN (", " NOT IN ("), new ArrayList<>(c.binds));
    }

    public Condition notIn(Object... values) {
        return notIn(values == null ? List.of() : Arrays.asList(values));
    }

    public Condition between(Object a, Object b) {
        return Condition.of(sql + " BETWEEN ? AND ?", a, b);
    }

    public Condition notBetween(Object a, Object b) {
        return Condition.of(sql + " NOT BETWEEN ? AND ?", a, b);
    }

    // ---------------- 排序 ----------------

    public OrderField<T> asc() {
        return new SortField<>(this, true);
    }

    public OrderField<T> desc() {
        return new SortField<>(this, false);
    }

    // ---------------- 聚合 / 表达式 ----------------

    public Field<BigDecimal> sum() {
        return new Field<>("sum(" + sql + ")", BigDecimal.class, new ArrayList<>(binds));
    }

    public Field<BigDecimal> avg() {
        return new Field<>("avg(" + sql + ")", BigDecimal.class, new ArrayList<>(binds));
    }

    public Field<T> min() {
        return new Field<>("min(" + sql + ")", type, new ArrayList<>(binds));
    }

    public Field<T> max() {
        return new Field<>("max(" + sql + ")", type, new ArrayList<>(binds));
    }

    public Field<Integer> count() {
        return new Field<>("count(" + sql + ")", Integer.class, new ArrayList<>(binds));
    }

    public Field<Integer> countDistinct() {
        return new Field<>("count(distinct " + sql + ")", Integer.class, new ArrayList<>(binds));
    }

    public Field<T> coalesce(Object value) {
        return new Field<>("coalesce(" + sql + ", ?)", type, SqlFragment.plus(this, value));
    }

    public Field<T> coalesce(Field<?> other) {
        return new Field<>("coalesce(" + sql + ", " + other.sql + ")", type, concat(this, other));
    }

    public Field<T> add(Object value) {
        return new Field<>("(" + sql + " + ?)", type, SqlFragment.plus(this, value));
    }

    /** 加法：右操作数可为另一个字段表达式（如 read_count + CASE ... END）。 */
    public Field<T> plus(Object value) {
        if (value instanceof Field<?> f) {
            return new Field<>("(" + sql + " + " + f.sql + ")", type, concat(this, f));
        }
        return add(value);
    }

    public Field<T> minus(Object value) {
        if (value instanceof Field<?> f) {
            return new Field<>("(" + sql + " - " + f.sql + ")", type, concat(this, f));
        }
        return sub(value);
    }

    public Field<T> sub(Object value) {
        return new Field<>("(" + sql + " - ?)", type, SqlFragment.plus(this, value));
    }

    public Field<T> mul(Object value) {
        return new Field<>("(" + sql + " * ?)", type, SqlFragment.plus(this, value));
    }

    public Field<T> div(Object value) {
        return new Field<>("(" + sql + " / ?)", type, SqlFragment.plus(this, value));
    }

    public Field<String> concat(Object... parts) {
        StringBuilder sb = new StringBuilder("concat(").append(sql);
        List<Object> b = new ArrayList<>(binds);
        for (Object p : parts) {
            sb.append(", ");
            if (p instanceof Field<?> f) {
                sb.append(f.sql);
                b.addAll(f.binds);
            } else {
                sb.append('?');
                b.add(p);
            }
        }
        return new Field<>(sb.append(')').toString(), String.class, b);
    }

    public Field<T> round(int scale) {
        return new Field<>("round(" + sql + ", " + scale + ")", type, new ArrayList<>(binds));
    }

    /** 供 SELECT 投影渲染：简单列不加别名，表达式加位置别名以便按序取值。 */
    String renderProjection(int index) {
        if (alias != null && !alias.isEmpty()) {
            return sql + " AS " + alias;
        }
        if (!isSimpleIdentifier()) {
            return sql + " AS __mb" + index;
        }
        return sql;
    }

    String resultKey(int index) {
        if (alias != null && !alias.isEmpty()) {
            return alias;
        }
        return isSimpleIdentifier() ? sql : "__mb" + index;
    }

    private List<Object> joinBinds(List<Object> inner) {
        List<Object> out = new ArrayList<>(binds);
        out.addAll(inner);
        return out;
    }

    private static String replacePlaceholders(String sql, int offset) {
        return sql;
    }
}
