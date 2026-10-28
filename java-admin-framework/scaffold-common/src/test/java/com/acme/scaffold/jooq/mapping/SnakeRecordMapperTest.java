package com.acme.scaffold.jooq.mapping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SnakeRecordMapper} 回归测试：MySQL {@code TINYINT(1)} 被 jOOQ 读为 {@link Boolean}，
 * 须能安全映射到 {@code Integer/Long} 等数值字段（修复前因无匹配分支抛 IllegalArgumentException）。
 *
 * <p>直接对核心转换方法 {@link SnakeRecordMapper#convert(Object, Class)} 做单测，避免依赖 jOOQ
 * {@code Record} 构造细节（不同 jOOQ 版本 {@code RecordImpl} 可见性不一致）。</p>
 */
class SnakeRecordMapperTest {

    @Test
    void tinyintBooleanTrue_mapsToIntegerOne() {
        assertEquals(Integer.valueOf(1), SnakeRecordMapper.convert(Boolean.TRUE, Integer.class),
                "TINYINT(1)=true 应映射为 Integer 1");
    }

    @Test
    void tinyintBooleanFalse_mapsToIntegerZero() {
        assertEquals(Integer.valueOf(0), SnakeRecordMapper.convert(Boolean.FALSE, Integer.class),
                "TINYINT(1)=false 应映射为 Integer 0");
    }

    @Test
    void booleanTrue_mapsToLongOne() {
        assertEquals(Long.valueOf(1L), SnakeRecordMapper.convert(Boolean.TRUE, Long.class),
                "TINYINT(1)=true 应映射为 Long 1");
    }

    @Test
    void nullValue_returnsNull() {
        assertNull(SnakeRecordMapper.convert(null, Integer.class), "null 值应原样返回 null");
    }

    @Test
    void numericValue_stillMapsToInteger() {
        assertEquals(Integer.valueOf(5), SnakeRecordMapper.convert(Integer.valueOf(5), Integer.class),
                "数值列仍应原样映射");
    }

    @Test
    void booleanTarget_acceptsBooleanValue() {
        assertEquals(Boolean.TRUE, SnakeRecordMapper.convert(Boolean.TRUE, Boolean.class),
                "Boolean 目标 + Boolean 值应直接返回");
    }

    @Test
    void stringTarget_acceptsStringValue() {
        assertEquals("ok", SnakeRecordMapper.convert("ok", String.class),
                "String 目标 + String 值应直接返回");
    }

    @Test
    void unsupportedTypeMismatch_throwsIllegalState() {
        // String 值无法转为 Integer → convert 原样返回 String，调用方 setter 抛 IllegalArgumentException，
        // 被 map() 包装为 IllegalStateException。这里验证 convert 自身在无法转换时返回非目标类型值
        // （即保留“原样返回”语义，由上层 setter 报错）。
        Object r = SnakeRecordMapper.convert("not-a-number", Integer.class);
        assertEquals("not-a-number", r, "无法转换的类型组合应原样返回，由上层 setter 报错");
    }
}
