package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** WHERE / HAVING / ON 条件表达式。所有值均以 ? 占位，由 MyBatis 绑定。 */
public class Condition extends SqlFragment {

    public static final Condition TRUE = new Condition("1 = 1", List.of());
    public static final Condition FALSE = new Condition("1 = 0", List.of());
    /** 空条件：DSL.noCondition() 的累加起点，组合时自动跳过。 */
    public static final Condition NONE = new Condition("", List.of());

    public Condition(String sql, List<Object> binds) {
        super(sql, binds);
    }

    public static Condition of(String sql, Object... binds) {
        return new Condition(sql, SqlFragment.list(binds));
    }

    public boolean isEmpty() {
        return sql.isEmpty();
    }

    public Condition and(Condition other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new Condition("(" + sql + ") AND (" + other.sql + ")", concat(this, other));
    }

    public Condition or(Condition other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new Condition("(" + sql + ") OR (" + other.sql + ")", concat(this, other));
    }

    public Condition and(String sql, Object... binds) {
        return and(of(sql, binds));
    }

    public Condition or(String sql, Object... binds) {
        return or(of(sql, binds));
    }

    public Condition not() {
        return isEmpty() ? this : new Condition("NOT (" + sql + ")", new ArrayList<>(binds));
    }

    /** 把一组条件以 AND 合并；空集合返回 NONE。 */
    static Condition all(Collection<? extends Condition> conditions) {
        Condition result = NONE;
        if (conditions != null) {
            for (Condition c : conditions) {
                result = result.and(c);
            }
        }
        return result;
    }

    static Condition any(Collection<? extends Condition> conditions) {
        Condition result = NONE;
        if (conditions != null) {
            for (Condition c : conditions) {
                result = result.or(c);
            }
        }
        return result;
    }

    static Condition all(Condition... conditions) {
        return conditions == null ? NONE : all(Arrays.asList(conditions));
    }

    static Condition any(Condition... conditions) {
        return conditions == null ? NONE : any(Arrays.asList(conditions));
    }
}
