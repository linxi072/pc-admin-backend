package com.acme.scaffold.mybatis.dsl;

/** 可执行语句（INSERT / UPDATE / DELETE / 原生 SQL）。 */
public interface Query {
    int execute();
    String getSQL();
}
