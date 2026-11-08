package com.acme.scaffold.mybatis.dsl;

/** Record -> POJO 映射器（SnakeRecordMapper 实现该接口）。 */
@FunctionalInterface
public interface RecordMapper<R extends Record, E> {
    E map(R record);
}
