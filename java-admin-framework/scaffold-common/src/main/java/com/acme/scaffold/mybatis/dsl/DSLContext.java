package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 数据访问入口（替代 jOOQ DSLContext）。
 *
 * <p>SQL 由 {@link SqlStatement} 渲染，执行统一委托给 MyBatis {@link DynamicMapper}，
 * 连接与事务由 Spring 管理的 {@code SqlSessionTemplate} 提供。</p>
 */
public class DSLContext {

    private final DynamicMapper mapper;
    private final List<ExecuteListener> listeners;
    /** 同一次执行内复用上下文，使 executeStart 写入的计时数据能被 executeEnd 读到。 */
    private final ThreadLocal<ExecuteContext> current = new ThreadLocal<>();

    public DSLContext(DynamicMapper mapper) {
        this(mapper, List.of());
    }

    public DSLContext(DynamicMapper mapper, List<ExecuteListener> listeners) {
        this.mapper = mapper;
        this.listeners = listeners == null ? List.of() : new ArrayList<>(listeners);
    }

    DynamicMapper mapper() {
        return mapper;
    }

    void fireStart(String sql) {
        ExecuteContext c = new ExecuteContext(sql);
        current.set(c);
        for (ExecuteListener l : listeners) {
            l.executeStart(c);
        }
    }

    void fireEnd(String sql) {
        ExecuteContext c = current.get();
        if (c == null) {
            c = new ExecuteContext(sql);
        }
        try {
            for (ExecuteListener l : listeners) {
                l.executeEnd(c);
            }
        } finally {
            current.remove();
        }
    }

    // ---------------- SELECT ----------------

    public <T> SqlStatement<Record1<T>> select(Field<T> f1) {
        return new SqlStatement<Record1<T>>(this, Record1.class).select(f1);
    }

    public <T1, T2> SqlStatement<Record2<T1, T2>> select(Field<T1> f1, Field<T2> f2) {
        return new SqlStatement<Record2<T1, T2>>(this, Record2.class).select(f1, f2);
    }

    public <T1, T2, T3> SqlStatement<Record3<T1, T2, T3>> select(Field<T1> f1, Field<T2> f2, Field<T3> f3) {
        return new SqlStatement<Record3<T1, T2, T3>>(this, Record3.class).select(f1, f2, f3);
    }

    public <T1, T2, T3, T4> SqlStatement<Record4<T1, T2, T3, T4>> select(
            Field<T1> f1, Field<T2> f2, Field<T3> f3, Field<T4> f4) {
        return new SqlStatement<Record4<T1, T2, T3, T4>>(this, Record4.class).select(f1, f2, f3, f4);
    }

    public <T1, T2, T3, T4, T5> SqlStatement<Record5<T1, T2, T3, T4, T5>> select(
            Field<T1> f1, Field<T2> f2, Field<T3> f3, Field<T4> f4, Field<T5> f5) {
        return new SqlStatement<Record5<T1, T2, T3, T4, T5>>(this, Record5.class).select(f1, f2, f3, f4, f5);
    }

    public <T1, T2, T3, T4, T5, T6> SqlStatement<Record6<T1, T2, T3, T4, T5, T6>> select(
            Field<T1> f1, Field<T2> f2, Field<T3> f3, Field<T4> f4, Field<T5> f5, Field<T6> f6) {
        return new SqlStatement<Record6<T1, T2, T3, T4, T5, T6>>(this, Record6.class).select(f1, f2, f3, f4, f5, f6);
    }

    public SqlStatement<Record> select(Field<?>... fields) {
        return new SqlStatement<Record>(this, Record.class).select(fields);
    }

    public SqlStatement<Record> select(Collection<? extends Field<?>> fields) {
        return new SqlStatement<Record>(this, Record.class).select(fields);
    }

    public SqlStatement<Record1<Integer>> selectCount() {
        SqlStatement<Record1<Integer>> q = new SqlStatement<>(this, Record1.class);
        q.projection("count(*)", Integer.class);
        return q;
    }

    public SqlStatement<Record1<Integer>> selectOne() {
        SqlStatement<Record1<Integer>> q = new SqlStatement<>(this, Record1.class);
        q.projection("1", Integer.class);
        return q;
    }

    public SqlStatement<Record> selectDistinct(Field<?>... fields) {
        SqlStatement<Record> q = new SqlStatement<>(this, Record.class).select(fields);
        q.setDistinct(true);
        return q;
    }

    public <R extends Record> SqlStatement<R> selectFrom(Table<R> table) {
        SqlStatement<R> q = new SqlStatement<>(this, Record.class);
        q.select();
        q.from(table);
        return q;
    }

    // ---------------- 写入 ----------------

    public SqlStatement<Record> insertInto(Table<?> table) {
        SqlStatement<Record> q = new SqlStatement<>(this, Record.class);
        q.configure("insert", table.renderFrom());
        return q;
    }

    public SqlStatement<Record> update(Table<?> table) {
        SqlStatement<Record> q = new SqlStatement<>(this, Record.class);
        q.configure("update", table.renderFrom());
        return q;
    }

    public SqlStatement<Record> deleteFrom(Table<?> table) {
        SqlStatement<Record> q = new SqlStatement<>(this, Record.class);
        q.configure("delete", table.renderFrom());
        return q;
    }

    public SqlStatement<Record> delete(Table<?> table) {
        return deleteFrom(table);
    }

    // ---------------- 原生 / 聚合辅助 ----------------

    public SqlStatement<Record> resultQuery(String sql, Object... binds) {
        SqlStatement<Record> q = new SqlStatement<>(this, Record.class);
        q.setRaw(sql, binds);
        return q;
    }

    public int execute(String sql, Object... binds) {
        SqlStatement<Record> q = new SqlStatement<>(this, Record.class);
        q.setRaw(sql, binds);
        return q.execute();
    }

    public int fetchCount(Select<?> select) {
        return ((SqlStatement<?>) select).fetchCount();
    }

    /** 兼容 dsl.fetchCount(table, condition) 写法。 */
    public int fetchCount(Table<?> table, Condition... conditions) {
        SqlStatement<Record1<Integer>> q = selectCount();
        q.from(table).where(conditions);
        Record1<Integer> row = q.fetchOne();
        Integer value = row == null ? null : row.value1();
        return value == null ? 0 : value;
    }

    public boolean fetchExists(Select<?> select) {
        return ((SqlStatement<?>) select).fetchExists();
    }

    public SqlBatch batch(Collection<? extends Query> queries) {
        return new SqlBatch(new ArrayList<>(queries));
    }

    public SqlBatch batch(Query... queries) {
        return new SqlBatch(queries == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(queries)));
    }

    /** 便于诊断：当前上下文持有的 MyBatis Mapper 类型。 */
    public Class<?> mapperType() {
        return mapper == null ? null : mapper.getClass();
    }
}
