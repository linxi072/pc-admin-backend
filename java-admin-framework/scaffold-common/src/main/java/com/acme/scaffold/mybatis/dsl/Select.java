package com.acme.scaffold.mybatis.dsl;

import java.util.List;

/** SELECT 查询的通用链式契约。所有方法返回同一个 SqlStatement，可任意串接。 */
public interface Select<R extends Record> {

    SqlStatement<R> from(Table<?> table);
    SqlStatement<R> from(String table);
    SqlStatement<R> where(Condition... conditions);
    SqlStatement<R> and(Condition condition);
    SqlStatement<R> or(Condition condition);
    SqlStatement<R> orderBy(OrderField<?>... fields);
    SqlStatement<R> groupBy(Field<?>... fields);
    SqlStatement<R> having(Condition condition);
    SqlStatement<R> limit(int rows);
    SqlStatement<R> offset(long rows);
    SqlStatement<R> seek(Object... values);
    SqlStatement<R> join(Table<?> table);
    SqlStatement<R> leftJoin(Table<?> table);
    SqlStatement<R> on(Condition condition);
    Result<R> fetch();
    <E> List<E> fetch(RecordMapper<R, E> mapper);
    <E> List<E> fetchInto(Class<E> type);
    R fetchOne();
    <E> E fetchOne(RecordMapper<R, E> mapper);
    <E> E fetchOneInto(Class<E> type);
    <T> T fetchOne(int index, Class<T> type);
    int fetchCount();
    boolean fetchExists();
    String getSQL();
}
