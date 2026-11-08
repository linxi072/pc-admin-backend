package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 动态 SQL 片段：一段带占位符的 SQL 文本 + 与其顺序一致的参数绑定表。
 *
 * <p>本包是「移除 jOOQ 后的等价 DSL 兼容层」：业务代码仍以链式条件表达式书写，
 * 但最终由 {@link DynamicMapper}（MyBatis {@code @SelectProvider} / {@code @UpdateProvider}）
 * 生成 PreparedStatement 并执行，参数一律走 {@code #{binds[i]}} 绑定，绝不拼接用户输入。</p>
 */
abstract class SqlFragment {

    final String sql;
    final List<Object> binds;

    SqlFragment(String sql, List<Object> binds) {
        this.sql = sql == null ? "" : sql;
        this.binds = binds == null ? new ArrayList<>() : new ArrayList<>(binds);
    }

    public final String sql() {
        return sql;
    }

    public final List<Object> binds() {
        return Collections.unmodifiableList(binds);
    }

    static List<Object> concat(SqlFragment a, SqlFragment b) {
        List<Object> out = new ArrayList<>(a.binds);
        out.addAll(b.binds);
        return out;
    }

    static List<Object> plus(SqlFragment a, Object... more) {
        List<Object> out = new ArrayList<>(a.binds);
        if (more != null) {
            out.addAll(Arrays.asList(more));
        }
        return out;
    }

    static List<Object> list(Object... values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(values));
    }

    @Override
    public String toString() {
        return sql;
    }
}
