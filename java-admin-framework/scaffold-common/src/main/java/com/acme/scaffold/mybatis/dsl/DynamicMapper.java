package com.acme.scaffold.mybatis.dsl;

import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

import java.util.List;
import java.util.Map;

/**
 * 通用动态 SQL Mapper：所有兼容层查询最终经此接口由 MyBatis SqlSession 执行。
 * 使用 Provider 动态构造 SQL，参数统一 #{binds[i]} 绑定。
 */
public interface DynamicMapper {

    @SelectProvider(type = DynamicSqlProvider.class, method = "sql")
    List<Map<String, Object>> select(DynamicStatement statement);

    @SelectProvider(type = DynamicSqlProvider.class, method = "sql")
    Map<String, Object> selectOne(DynamicStatement statement);

    @UpdateProvider(type = DynamicSqlProvider.class, method = "sql")
    int update(DynamicStatement statement);

    @InsertProvider(type = DynamicSqlProvider.class, method = "sql")
    int insert(DynamicStatement statement);

    @DeleteProvider(type = DynamicSqlProvider.class, method = "sql")
    int delete(DynamicStatement statement);
}
