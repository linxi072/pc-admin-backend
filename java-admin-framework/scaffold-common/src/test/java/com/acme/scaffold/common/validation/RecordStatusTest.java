package com.acme.scaffold.common.validation;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RecordStatus} 单测：覆盖 null / 空白回落、大小写不敏感归一、非法值抛错三类行为。
 */
class RecordStatusTest {

    @Test
    void normalizeNullFallsBackToActive() {
        assertEquals("ACTIVE", RecordStatus.normalize(null));
    }

    @Test
    void normalizeBlankFallsBackToActive() {
        assertEquals("ACTIVE", RecordStatus.normalize(""));
        assertEquals("ACTIVE", RecordStatus.normalize("   "));
        assertEquals("ACTIVE", RecordStatus.normalize("\t\n"));
    }

    @Test
    void normalizeCanonicalValuesUnchanged() {
        assertEquals("ACTIVE", RecordStatus.normalize("ACTIVE"));
        assertEquals("DISABLED", RecordStatus.normalize("DISABLED"));
        assertEquals("INACTIVE", RecordStatus.normalize("INACTIVE"));
        assertEquals("LOCKED", RecordStatus.normalize("LOCKED"));
    }

    @Test
    void normalizeCaseInsensitiveAndTrimmed() {
        assertEquals("ACTIVE", RecordStatus.normalize("active"));
        assertEquals("DISABLED", RecordStatus.normalize("  Disabled "));
        assertEquals("INACTIVE", RecordStatus.normalize("InActive"));
        assertEquals("LOCKED", RecordStatus.normalize("locked"));
    }

    @Test
    void normalizeThrowsForIllegalValue() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> RecordStatus.normalize("FOO"));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());

        // 越界大小写变体同样拒绝
        assertThrows(BusinessException.class, () -> RecordStatus.normalize("activex"));
        assertThrows(BusinessException.class, () -> RecordStatus.normalize("  DELETED "));
    }

    @Test
    void namesWhitelistHasFourMembers() {
        assertEquals(4, RecordStatus.NAMES.size());
    }
}
