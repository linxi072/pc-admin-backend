package com.acme.scaffold.security.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.acme.scaffold.common.sensitive.SensitiveType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 列权限执行器单测：通用掩码兜底与 record 重建失败时的 fail-closed 语义。 */
class ColumnPermissionApplierTest {

    private final ColumnPermissionApplier applier = new ColumnPermissionApplier(null);

    @Test
    void maskValueShouldFallbackToGenericMaskForNullAndNone() {
        assertEquals("abc****hijk", ColumnPermissionApplier.maskValue("abcdefghijk", null));
        assertEquals("abc****hijk", ColumnPermissionApplier.maskValue("abcdefghijk", SensitiveType.NONE));
    }

    @Test
    void recordShouldBeRebuiltWithMaskedCopy() {
        Plain original = new Plain("abcdefghijk", "Tom");
        List<ColumnPermissionFieldView> fields = List.of(
                new ColumnPermissionFieldView("mobile", "手机号", ColumnStrategy.MASK, null));

        Plain result = applier.apply(original, "system:user", fields, code -> false);

        assertNotSame(original, result);
        assertEquals("abc****hijk", result.mobile());
        assertEquals("abcdefghijk", original.mobile());
        assertEquals("Tom", result.name());
    }

    @Test
    void recordRebuildFailureShouldNotReturnOriginalPlaintext() {
        Strict original = new Strict("abcdefghijk");
        List<ColumnPermissionFieldView> fields = List.of(
                new ColumnPermissionFieldView("secret", "密钥", ColumnStrategy.MASK, null));

        assertThrows(IllegalStateException.class,
                () -> applier.apply(original, "system:user", fields, code -> false));
    }

    record Plain(String mobile, String name) {
    }

    record Strict(String secret) {
        Strict {
            // 紧凑构造器拒绝已掩码值，模拟 record 重建失败场景。
            if ("abc****hijk".equals(secret)) {
                throw new IllegalArgumentException("masked value rejected");
            }
        }
    }
}
