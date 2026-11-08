package com.acme.scaffold.mybatis.dsl;

import java.util.List;

/** ORDER BY 项。 */
public interface OrderField<T> {
    String sql();
    List<Object> binds();

    /** MySQL 无 NULLS LAST 语法，由 SortField 覆盖为 (col IS NULL), col DIR 复合排序。 */
    default OrderField<T> nullsLast() {
        return this;
    }

    default OrderField<T> nullsFirst() {
        return this;
    }
}
