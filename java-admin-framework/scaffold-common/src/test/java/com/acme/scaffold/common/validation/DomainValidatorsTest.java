package com.acme.scaffold.common.validation;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DomainValidators} 单测：覆盖枚举白名单、数值范围、非负三类通用校验原语。
 */
class DomainValidatorsTest {

    enum Color {
        RED, GREEN, BLUE
    }

    @Test
    void requireInPassesForValidString() {
        assertEquals("RED", DomainValidators.requireIn("RED",
                new String[]{"RED", "GREEN", "BLUE"}, "bad"));
    }

    @Test
    void requireInPassesForValidEnumSubset() {
        assertEquals(Color.RED, DomainValidators.requireIn(Color.RED,
                new Color[]{Color.RED, Color.GREEN}, "bad"));
    }

    @Test
    void requireInThrowsForInvalidString() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DomainValidators.requireIn("YELLOW",
                        new String[]{"RED", "GREEN", "BLUE"}, "bad color"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void requireInThrowsForEnumNotInSubset() {
        assertThrows(BusinessException.class, () -> DomainValidators.requireIn(Color.BLUE,
                new Color[]{Color.RED, Color.GREEN}, "bad color"));
    }

    @Test
    void requireInEnumNamePassesAndFails() {
        // 字符串 → 枚举名白名单（如模板渠道名 → Channel）
        DomainValidators.requireIn("RED", Color.values(), "bad color");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DomainValidators.requireIn("YELLOW", Color.values(), "bad color"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertThrows(BusinessException.class, () -> DomainValidators.requireIn(null, Color.values(), "null"));
    }

    @Test
    void requireInThrowsForNull() {
        assertThrows(BusinessException.class, () -> DomainValidators.requireIn(null,
                new String[]{"RED"}, "null not allowed"));
    }

    @Test
    void requireRangeLongPassAndFail() {
        assertEquals(5L, DomainValidators.requireRange(5L, 0L, 100L, "out"));
        assertThrows(BusinessException.class, () -> DomainValidators.requireRange(150L, 0L, 100L, "out"));
        assertThrows(BusinessException.class, () -> DomainValidators.requireRange(-1L, 0L, 100L, "out"));
    }

    @Test
    void requireRangeIntPassAndFail() {
        assertEquals(50, DomainValidators.requireRange(50, 0, 100, "out"));
        assertThrows(BusinessException.class, () -> DomainValidators.requireRange(101, 0, 100, "out"));
        assertThrows(BusinessException.class, () -> DomainValidators.requireRange(-5, 0, 100, "out"));
    }

    @Test
    void requireNonNegativeLong() {
        assertEquals(0L, DomainValidators.requireNonNegative(0L, "neg"));
        assertThrows(BusinessException.class, () -> DomainValidators.requireNonNegative(-5L, "neg"));
    }

    @Test
    void requireNonNegativeInt() {
        assertEquals(3, DomainValidators.requireNonNegative(3, "neg"));
        assertThrows(BusinessException.class, () -> DomainValidators.requireNonNegative(-1, "neg"));
    }
}
