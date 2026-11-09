package com.acme.scaffold.mybatis.dsl;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** DSL 静态工厂：条件、字段、聚合、CASE、子查询。 */
public final class DSL {

    private DSL() {
    }

    // ---------------- 名称与表 ----------------

    public static String name(String value) {
        return value;
    }

    /** 限定列名，如 DSL.name("su", "user_id") -> "su.user_id"。 */
    public static String name(String qualifier, String column) {
        return qualifier + "." + column;
    }

    public static Table<Record> table(String name) {
        return new Table<>(name);
    }

    public static Table<Record> table(NameHolder name) {
        return new Table<>(name.value());
    }

    /** 兼容 DSL.name(x) 返回值的轻量包装（若调用方显式声明）。 */
    public static final class NameHolder {
        private final String value;
        public NameHolder(String value) { this.value = value; }
        public String value() { return value; }
    }

    // ---------------- 字段 ----------------

    public static <T> Field<T> field(String name, Class<T> type) {
        return new Field<>(name, type);
    }

    public static Field<Object> field(String expression) {
        return new Field<>(expression, Object.class);
    }

    public static <T> Field<T> field(String name, Class<T> type, List<Object> binds) {
        return new Field<>(name, type, binds);
    }

    /**
     * jOOQ 风格模板字段：{0} / {1} ... 由 QueryPart（Field）或绑定值替换，
     * 例如 {@code DSL.field("avg({0})", BigDecimal.class, duration)}。
     */
    public static <T> Field<T> field(String template, Class<T> type, Object... parts) {
        StringBuilder out = new StringBuilder();
        List<Object> binds = new java.util.ArrayList<>();
        int positional = 0;
        for (int i = 0; i < template.length(); ) {
            char c = template.charAt(i);
            if (c == '{') {
                int close = template.indexOf('}', i);
                if (close > i + 1) {
                    try {
                        int n = Integer.parseInt(template.substring(i + 1, close).trim());
                        Object p = (parts != null && n < parts.length) ? parts[n] : null;
                        appendPart(out, binds, p);
                        i = close + 1;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // 非数字占位，按普通字符处理
                    }
                }
            }
            if (c == '?' && parts != null && positional < parts.length) {
                out.append('?');
                binds.add(parts[positional++]);
            } else {
                out.append(c);
            }
            i++;
        }
        return new Field<>(out.toString(), type, binds);
    }

    private static void appendPart(StringBuilder out, List<Object> binds, Object part) {
        if (part instanceof Field<?> f) {
            out.append(f.sql());
            binds.addAll(f.binds());
        } else {
            out.append('?');
            binds.add(part);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Field<T> val(T value) {
        Class<T> type = (Class<T>) (value == null ? Object.class : value.getClass());
        List<Object> binds = new java.util.ArrayList<>();
        binds.add(value);
        return new Field<>("?", type, binds);
    }

    // ---------------- 条件 ----------------

    public static Condition trueCondition() {
        return Condition.TRUE;
    }

    public static Condition falseCondition() {
        return Condition.FALSE;
    }

    public static Condition noCondition() {
        return Condition.NONE;
    }

    public static Condition condition(String sql, Object... binds) {
        return Condition.of(sql, binds);
    }

    public static Condition condition(Condition condition) {
        return condition == null ? Condition.NONE : condition;
    }

    public static Condition and(Condition... conditions) {
        return Condition.all(conditions);
    }

    public static Condition and(Collection<? extends Condition> conditions) {
        return Condition.all(conditions);
    }

    public static Condition or(Condition... conditions) {
        return Condition.any(conditions);
    }

    public static Condition or(Collection<? extends Condition> conditions) {
        return Condition.any(conditions);
    }

    public static Condition notExists(Select<?> select) {
        SqlStatement<?> q = (SqlStatement<?>) select;
        return new Condition("NOT EXISTS (" + q.renderSql() + ")", q.renderBinds());
    }

    public static Condition exists(Select<?> select) {
        SqlStatement<?> q = (SqlStatement<?>) select;
        return new Condition("EXISTS (" + q.renderSql() + ")", q.renderBinds());
    }

    // ---------------- 聚合 ----------------

    public static Field<Integer> count() {
        return new Field<>("count(*)", Integer.class);
    }

    public static Field<Integer> count(Field<?> field) {
        return field.count();
    }

    public static Field<Integer> countDistinct(Field<?> field) {
        return field.countDistinct();
    }

    public static Field<BigDecimal> sum(Field<?> field) {
        return field.sum();
    }

    public static Field<BigDecimal> avg(Field<?> field) {
        return field.avg();
    }

    @SuppressWarnings("unchecked")
    public static <T> Field<T> max(Field<T> field) {
        return field.max();
    }

    @SuppressWarnings("unchecked")
    public static <T> Field<T> min(Field<T> field) {
        return field.min();
    }

    @SuppressWarnings("unchecked")
    public static <T> Field<T> coalesce(Field<T> field, Object value) {
        return field.coalesce(value);
    }

    public static Field<String> concat(Object... parts) {
        StringBuilder sb = new StringBuilder("concat(");
        List<Object> binds = new java.util.ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", ");
            Object p = parts[i];
            if (p instanceof Field<?> f) {
                sb.append(f.sql());
                binds.addAll(f.binds());
            } else {
                sb.append('?');
                binds.add(p);
            }
        }
        return new Field<>(sb.append(')').toString(), String.class, binds);
    }

    public static <T> CaseStep<T> when(Condition condition, T value) {
        return new CaseStep<>(condition, value);
    }

    // ---------------- 独立查询（子查询用，不直接执行） ----------------

    public static <T> SqlStatement<Record1<T>> select(Field<T> field) {
        return new SqlStatement<Record1<T>>(null, Record1.class).select(field);
    }

    public static <T1, T2> SqlStatement<Record2<T1, T2>> select(Field<T1> f1, Field<T2> f2) {
        return new SqlStatement<Record2<T1, T2>>(null, Record2.class).select(f1, f2);
    }

    public static <T1, T2, T3> SqlStatement<Record3<T1, T2, T3>> select(
            Field<T1> f1, Field<T2> f2, Field<T3> f3) {
        return new SqlStatement<Record3<T1, T2, T3>>(null, Record3.class).select(f1, f2, f3);
    }

    public static SqlStatement<Record> select(Field<?>... fields) {
        return new SqlStatement<Record>(null, Record.class).select(fields);
    }

    public static SqlStatement<Record> select(Collection<? extends Field<?>> fields) {
        return new SqlStatement<Record>(null, Record.class).select(fields);
    }

    public static SqlStatement<Record1<Integer>> selectOne() {
        return new SqlStatement<Record1<Integer>>(null, Record1.class).selectOne();
    }

    public static DSLContext using(Object configuration) {
        throw new UnsupportedOperationException("jOOQ DSL.using 已移除，请注入 MyBatis DSLContext");
    }

}
