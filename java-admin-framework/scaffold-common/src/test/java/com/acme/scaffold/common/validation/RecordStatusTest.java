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

    @Test
    void mergeOrKeepKeepsExistingWhenIncomingNull() {
        // update 场景：未传状态 → 保留原值（而非回落 DEFAULT）
        assertEquals("LOCKED", RecordStatus.mergeOrKeep(null, "LOCKED"));
        assertEquals("DISABLED", RecordStatus.mergeOrKeep(null, "DISABLED"));
    }

    @Test
    void mergeOrKeepNormalizesNonBlankIncoming() {
        assertEquals("ACTIVE", RecordStatus.mergeOrKeep("active", "LOCKED"));
        assertEquals("DISABLED", RecordStatus.mergeOrKeep("  Disabled ", "ACTIVE"));
    }

    @Test
    void mergeOrKeepBlankIncomingFallsBackToActiveNotExisting() {
        // 空白非 null，走 normalize → DEFAULT，而非保留 existing
        assertEquals("ACTIVE", RecordStatus.mergeOrKeep("", "LOCKED"));
        assertEquals("ACTIVE", RecordStatus.mergeOrKeep("   ", "DISABLED"));
    }

    @Test
    void mergeOrKeepThrowsForIllegalIncoming() {
        assertThrows(BusinessException.class, () -> RecordStatus.mergeOrKeep("FOO", "ACTIVE"));
    }
}
