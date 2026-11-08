package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.List;

/** 带 ASC / DESC 与 NULLS FIRST / LAST 语义的排序项。 */
final class SortField<T> extends SqlFragment implements OrderField<T> {

    private final String column;
    private final boolean ascending;
    private final int nullsMode; // 0=不处理 1=NULLS LAST 2=NULLS FIRST

    SortField(Field<T> field, boolean ascending) {
        this(field.sql(), ascending, 0, new ArrayList<>(field.binds));
    }

    private SortField(String column, boolean ascending, int nullsMode, List<Object> binds) {
        super(render(column, ascending, nullsMode), binds);
        this.column = column;
        this.ascending = ascending;
        this.nullsMode = nullsMode;
    }

    private static String render(String column, boolean ascending, int nullsMode) {
        String dir = ascending ? "ASC" : "DESC";
        if (nullsMode == 1) {
            // MySQL：col IS NULL 对非 NULL 为 0，升序时非 NULL 在前
            return "(" + column + " IS NULL), " + column + " " + dir;
        }
        if (nullsMode == 2) {
            return "(" + column + " IS NOT NULL), " + column + " " + dir;
        }
        return column + " " + dir;
    }

    @Override
    public OrderField<T> nullsLast() {
        return new SortField<>(column, ascending, 1, new ArrayList<>(binds));
    }

    @Override
    public OrderField<T> nullsFirst() {
        return new SortField<>(column, ascending, 2, new ArrayList<>(binds));
    }
}
