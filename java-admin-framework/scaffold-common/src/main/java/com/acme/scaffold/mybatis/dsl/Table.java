package com.acme.scaffold.mybatis.dsl;

import java.util.List;

/** 表引用。表名来自 SqlTables 的静态登记，不接受用户输入。 */
public class Table<R extends Record> extends SqlFragment {

    private final String alias;

    public Table(String name) {
        this(name, null);
    }

    public Table(String name, String alias) {
        super(name, List.of());
        this.alias = alias;
    }

    public String name() {
        return sql;
    }

    public String alias() {
        return alias;
    }

    public Table<R> as(String newAlias) {
        return new Table<>(sql, newAlias);
    }

    public <T> Field<T> field(String column, Class<T> type) {
        return new Field<>(column, type);
    }

    public Field<Object> field(String column) {
        return new Field<>(column, Object.class);
    }

    String renderFrom() {
        return alias == null ? sql : sql + " " + alias;
    }

    @Override
    public Table<R> clone() {
        return new Table<>(sql, alias);
    }
}
