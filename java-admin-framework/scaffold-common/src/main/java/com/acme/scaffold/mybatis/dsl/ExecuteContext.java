package com.acme.scaffold.mybatis.dsl;

import java.util.HashMap;
import java.util.Map;

/** 一次 SQL 执行的上下文，供观测监听器使用。 */
public class ExecuteContext {

    private final String sql;
    private final Map<String, Object> data = new HashMap<>();

    public ExecuteContext(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }

    public Object data(String key) {
        return data.get(key);
    }

    public void data(String key, Object value) {
        data.put(key, value);
    }
}
