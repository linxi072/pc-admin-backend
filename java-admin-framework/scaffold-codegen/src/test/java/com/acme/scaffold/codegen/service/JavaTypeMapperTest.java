package com.acme.scaffold.codegen.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDBC 类型 → Java 类型映射纯函数测试（information_schema DATA_TYPE → 生成源码类型）。
 */
class JavaTypeMapperTest {

    @Test
    void tinyintShouldMapBooleanOrIntegerByTinyintOne() {
        assertEquals("Boolean", JavaTypeMapper.toJavaType("tinyint", true));
        assertEquals("Integer", JavaTypeMapper.toJavaType("tinyint", false));
    }

    @Test
    void integerFamilyShouldMapCorrectly() {
        assertEquals("Integer", JavaTypeMapper.toJavaType("int", false));
        assertEquals("Integer", JavaTypeMapper.toJavaType("integer", false));
        assertEquals("Integer", JavaTypeMapper.toJavaType("smallint", false));
        assertEquals("Integer", JavaTypeMapper.toJavaType("mediumint", false));
        assertEquals("Long", JavaTypeMapper.toJavaType("bigint", false));
    }

    @Test
    void decimalAndFloatingShouldMapCorrectly() {
        assertEquals("BigDecimal", JavaTypeMapper.toJavaType("decimal", false));
        assertEquals("BigDecimal", JavaTypeMapper.toJavaType("numeric", false));
        assertEquals("Float", JavaTypeMapper.toJavaType("float", false));
        assertEquals("Double", JavaTypeMapper.toJavaType("double", false));
        assertEquals("Double", JavaTypeMapper.toJavaType("real", false));
    }

    @Test
    void stringFamilyShouldMapToString() {
        for (String t : new String[]{"char", "varchar", "text", "tinytext", "mediumtext",
                "longtext", "json", "enum", "set"}) {
            assertEquals("String", JavaTypeMapper.toJavaType(t, false), t);
        }
    }

    @Test
    void timeFamilyShouldMapCorrectly() {
        assertEquals("LocalDate", JavaTypeMapper.toJavaType("date", false));
        assertEquals("LocalTime", JavaTypeMapper.toJavaType("time", false));
        assertEquals("LocalDateTime", JavaTypeMapper.toJavaType("datetime", false));
        assertEquals("LocalDateTime", JavaTypeMapper.toJavaType("timestamp", false));
    }

    @Test
    void binaryFamilyShouldMapToByteArray() {
        for (String t : new String[]{"binary", "varbinary", "blob", "tinyblob",
                "mediumblob", "longblob"}) {
            assertEquals("byte[]", JavaTypeMapper.toJavaType(t, false), t);
        }
    }

    @Test
    void nullBlankOrUnknownShouldFallbackToString() {
        assertEquals("String", JavaTypeMapper.toJavaType(null, false));
        assertEquals("String", JavaTypeMapper.toJavaType("  ", false));
        assertEquals("String", JavaTypeMapper.toJavaType("geometry", false));
    }

    @Test
    void inputCaseShouldBeNormalized() {
        assertEquals("LocalDateTime", JavaTypeMapper.toJavaType("DATETIME", false));
        assertEquals("BigDecimal", JavaTypeMapper.toJavaType(" DECIMAL ", false));
    }

    @Test
    void needsImportShouldCoverMathAndTimeOnly() {
        assertTrue(JavaTypeMapper.needsImport("BigDecimal"));
        assertTrue(JavaTypeMapper.needsImport("LocalDate"));
        assertTrue(JavaTypeMapper.needsImport("LocalDateTime"));
        assertFalse(JavaTypeMapper.needsImport("String"));
        assertFalse(JavaTypeMapper.needsImport("Long"));
        assertFalse(JavaTypeMapper.needsImport("byte[]"));
    }

    @Test
    void toJavaFieldShouldConvertSnakeCase() {
        assertEquals("userName", JavaTypeMapper.toJavaField("user_name"));
        assertEquals("id", JavaTypeMapper.toJavaField("id"));
        assertEquals("createdAt", JavaTypeMapper.toJavaField("created_at"));
        assertEquals("field", JavaTypeMapper.toJavaField(null));
        assertEquals("field", JavaTypeMapper.toJavaField("  "));
    }

    @Test
    void toClassNameShouldStripPrefixAndPascalCase() {
        assertEquals("User", JavaTypeMapper.toClassName("sys_user"));
        assertEquals("BizOrder", JavaTypeMapper.toClassName("biz_order"));
        assertEquals("Order", JavaTypeMapper.toClassName("t_order"));
        assertEquals("Order", JavaTypeMapper.toClassName("order"));
        assertEquals("Entity", JavaTypeMapper.toClassName(null));
        assertEquals("Entity", JavaTypeMapper.toClassName("   "));
    }
}
