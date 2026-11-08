package com.acme.scaffold.mybatis.dsl;

/** SQL 执行观测钩子（慢 SQL、N+1 检测）。 */
public interface ExecuteListener {

    default void executeStart(ExecuteContext ctx) {
    }

    default void executeEnd(ExecuteContext ctx) {
    }
}
