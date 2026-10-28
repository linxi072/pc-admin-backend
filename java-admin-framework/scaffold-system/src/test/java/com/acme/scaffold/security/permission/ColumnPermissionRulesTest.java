package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段级数据权限纯函数规则测试：权限码派生、字段合并去重、校验。
 */
class ColumnPermissionRulesTest {

    @Test
    void columnPermissionCode_derivesExpectedCode() {
        assertEquals("system:user:column:mobile",
                ColumnPermissionRules.columnPermissionCode("system:user", "mobile"));
    }

    @Test
    void mergeFields_dedupByNameKeepsFirst() {
        List<ColumnPermissionFieldView> merged = ColumnPermissionRules.mergeFields(List.of(
                columnField("mobile", "手机号", ColumnStrategy.MASK, SensitiveType.MOBILE),
                columnField("email", "邮箱", ColumnStrategy.MASK, SensitiveType.EMAIL),
                columnField("mobile", "手机（重复）", ColumnStrategy.HIDE, SensitiveType.NONE)));
        assertEquals(2, merged.size());
        var mobile = merged.get(0);
        assertEquals("mobile", mobile.field());
        // 重复的 mobile 被忽略，保留首次出现的 MASK/MOBILE
        assertEquals(ColumnStrategy.MASK, mobile.strategy());
        assertEquals(SensitiveType.MOBILE, mobile.sensitiveType());
    }

    @Test
    void validate_rejectsEmptyResourceCodeAndEmptyFields() {
        ColumnPermission badCode = new ColumnPermission() {
            @Override
            public Class<ColumnPermission> annotationType() {
                return ColumnPermission.class;
            }

            @Override
            public String resourceCode() {
                return "";
            }

            @Override
            public String name() {
                return "";
            }

            @Override
            public ColumnField[] fields() {
                return new ColumnField[0];
            }
        };
        List<String> errors = ColumnPermissionRules.validate(badCode);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("resourceCode")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("fields")));
    }

    private static ColumnField columnField(String name, String label, ColumnStrategy strategy, SensitiveType type) {
        return new ColumnField() {
            @Override
            public Class<ColumnField> annotationType() {
                return ColumnField.class;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public ColumnStrategy strategy() {
                return strategy;
            }

            @Override
            public SensitiveType sensitiveType() {
                return type;
            }
        };
    }
}
