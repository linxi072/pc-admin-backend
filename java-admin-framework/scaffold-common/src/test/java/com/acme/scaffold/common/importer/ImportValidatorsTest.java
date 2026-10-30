package com.acme.scaffold.common.importer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯函数测试：导入字段校验构件。
 */
class ImportValidatorsTest {

    @Test
    void requiredRejectsNullAndBlank() {
        assertNull(ImportValidators.required("x", "字段"));
        assertNotNull(ImportValidators.required(null, "字段"));
        assertNotNull(ImportValidators.required("", "字段"));
        assertNotNull(ImportValidators.required("   ", "字段"));
        assertTrue(ImportValidators.required(null, "字段").contains("字段"));
    }

    @Test
    void maxLengthCountsCharacters() {
        assertNull(ImportValidators.maxLength("abc", "字段", 3));
        assertNull(ImportValidators.maxLength(null, "字段", 3));
        assertNotNull(ImportValidators.maxLength("abcd", "字段", 3));
        // 按字符数而非字节数（中文 3 字应通过长度为 3 的校验）
        assertNull(ImportValidators.maxLength("中文三", "字段", 3));
    }

    @Test
    void integerRangeHonoursOptionalAndBounds() {
        assertNull(ImportValidators.integerRange("5", "排序", 0, 10, false));
        assertNull(ImportValidators.integerRange("0", "排序", 0, 10, false));
        assertNull(ImportValidators.integerRange("10", "排序", 0, 10, false));
        assertNotNull(ImportValidators.integerRange("11", "排序", 0, 10, false));
        assertNotNull(ImportValidators.integerRange("abc", "排序", 0, 10, false));
        // 可选：空值通过；必填：空值报错
        assertNull(ImportValidators.integerRange("", "排序", 0, 10, true));
        assertNotNull(ImportValidators.integerRange("", "排序", 0, 10, false));
        // 允许首尾空白
        assertNull(ImportValidators.integerRange(" 7 ", "排序", 0, 10, false));
    }

    @Test
    void oneOfIsCaseInsensitiveAndOptionalAware() {
        assertNull(ImportValidators.oneOf("ACTIVE", "状态", true, "ACTIVE", "INACTIVE"));
        assertNull(ImportValidators.oneOf("active", "状态", true, "ACTIVE", "INACTIVE"));
        assertNotNull(ImportValidators.oneOf("DELETED", "状态", true, "ACTIVE", "INACTIVE"));
        assertNull(ImportValidators.oneOf("", "状态", true, "ACTIVE"));
        assertNotNull(ImportValidators.oneOf("", "状态", false, "ACTIVE"));
        assertTrue(ImportValidators.oneOf("DELETED", "状态", true, "ACTIVE", "INACTIVE")
                .contains("ACTIVE / INACTIVE"));
    }

    @Test
    void matchesUsesWholeMatchWithHint() {
        Pattern code = Pattern.compile("[a-z0-9_]{2,}");
        assertNull(ImportValidators.matches("user_status", "编码", code, false, null));
        assertNull(ImportValidators.matches("", "编码", code, true, null));
        assertNotNull(ImportValidators.matches("", "编码", code, false, null));
        String err = ImportValidators.matches("Bad-Code", "编码", code, false, "仅小写字母/数字/下划线");
        assertNotNull(err);
        assertTrue(err.contains("仅小写字母/数字/下划线"));
    }

    @Test
    void collectDropsNullsAndBlankMessagesAndKeepsOrder() {
        List<String> empty = ImportValidators.collect(
                ImportValidators.required("x", "a"), ImportValidators.required("y", "b"));
        assertTrue(empty.isEmpty());

        List<String> errors = ImportValidators.collect(
                ImportValidators.required("", "a"),
                ImportValidators.required("x", "b"),
                ImportValidators.required("", "c"));
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).contains("a"));
        assertTrue(errors.get(1).contains("c"));

        assertTrue(ImportValidators.collect().isEmpty());
        assertTrue(ImportValidators.collect((String[]) null).isEmpty());
    }
}
