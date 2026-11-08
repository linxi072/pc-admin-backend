package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * 交给 MyBatis 的动态语句：内部 SQL 以 ? 占位，渲染时替换为 #{binds[i]} 参数绑定。
 * 列名 / 表达式全部由服务端白名单构造，用户输入只出现在 binds 中，杜绝 SQL 注入。
 */
public class DynamicStatement {

    private final String sql;
    private final List<Object> binds;

    public DynamicStatement(String sql, List<Object> binds) {
        this.sql = sql;
        this.binds = binds == null ? new ArrayList<>() : new ArrayList<>(binds);
    }

    public List<Object> getBinds() {
        return binds;
    }

    /** MyBatis Provider 返回的最终 SQL。 */
    public String getSql() {
        StringBuilder out = new StringBuilder();
        int index = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?') {
                Object v = index < binds.size() ? binds.get(index) : null;
                out.append("#{binds[").append(index).append(']');
                if (v == null) {
                    // 显式 jdbcType，规避 MySQL 对 JdbcType.OTHER 的 setNull 兼容问题
                    out.append(",jdbcType=VARCHAR");
                }
                out.append('}');
                index++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public String rawSql() {
        return sql;
    }

    @Override
    public String toString() {
        return sql;
    }
}
