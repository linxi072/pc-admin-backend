package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.List;

/** CASE WHEN 表达式构造器。 */
public class CaseStep<T> {

    private final StringBuilder sql = new StringBuilder("CASE");
    private final List<Object> binds = new ArrayList<>();
    private final Class<T> type;

    @SuppressWarnings("unchecked")
    public CaseStep(Condition condition, Object value) {
        this.type = (Class<T>) (value == null ? Object.class : value.getClass());
        when(condition, value);
    }

    public CaseStep<T> when(Condition condition, Object value) {
        sql.append(" WHEN ").append(condition.sql()).append(" THEN ?");
        binds.addAll(condition.binds());
        binds.add(value);
        return this;
    }

    public Field<T> otherwise(Object value) {
        sql.append(" ELSE ?");
        binds.add(value);
        return new Field<>(sql.append(" END").toString(), type, binds);
    }

    public Field<T> else_(Object value) {
        return otherwise(value);
    }

    public Field<T> end() {
        return new Field<>(sql.append(" END").toString(), type, binds);
    }
}
