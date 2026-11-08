package com.acme.scaffold.mybatis.dsl;

/** MyBatis SQL Provider：把动态语句渲染为带 #{...} 绑定的 SQL。 */
public class DynamicSqlProvider {

    public String sql(DynamicStatement statement) {
        return statement.getSql();
    }
}
