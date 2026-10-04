package com.acme.scaffold.jooq;

import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.RecordMapperProvider;
import org.jooq.RecordType;

/**
 * 全局 RecordMapper 提供者：统一使用 {@link SnakeRecordMapper} 完成 snake_case 列到 camelCase POJO 的映射。
 */
public class SnakeRecordMapperProvider implements RecordMapperProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <R extends Record, E> RecordMapper<R, E> provide(RecordType<R> recordType, Class<? extends E> targetType) {
        return (RecordMapper<R, E>) new SnakeRecordMapper<>((Class<E>) targetType);
    }
}
