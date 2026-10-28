package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 字段级数据权限执行器测试：校验按当前主体列权限对受控字段脱敏/隐藏，
 * 以及无权限时回落通用掩码、集合批量处理、不可变类型跳过等失败安全行为。
 */
class ColumnPermissionApplierTest {

    /** 可变目标对象（模拟业务 DTO / 实体）。 */
    static class Account {
        String mobile;
        String email;
        String username;

        Account(String mobile, String email, String username) {
            this.mobile = mobile;
            this.email = email;
            this.username = username;
        }
    }

    private static final List<ColumnPermissionFieldView> FIELDS = List.of(
            new ColumnPermissionFieldView("mobile", "手机号", ColumnStrategy.MASK, SensitiveType.MOBILE),
            new ColumnPermissionFieldView("email", "邮箱", ColumnStrategy.MASK, SensitiveType.EMAIL),
            new ColumnPermissionFieldView("username", "用户名", ColumnStrategy.HIDE, SensitiveType.NONE));

    private ColumnPermissionApplier applierWithRegistry() {
        ColumnPermissionResourceRegistry registry = new ColumnPermissionResourceRegistry();
        registry.register(new ColumnPermission() {
            @Override
            public Class<ColumnPermission> annotationType() {
                return ColumnPermission.class;
            }

            @Override
            public String resourceCode() {
                return "system:account";
            }

            @Override
            public String name() {
                return "账户";
            }

            @Override
            public ColumnField[] fields() {
                return new ColumnField[0];
            }
        });
        // 直接注入已含字段的注册表（绕过注解扫描，聚焦执行逻辑）
        return new ColumnPermissionApplier(registry);
    }

    @Test
    void apply_withoutPermission_masksAndHides() {
        ColumnPermissionApplier applier = applierWithRegistry();
        Account acc = new Account("13800001234", "zhang@example.com", "alice");

        applier.apply(acc, "system:account", FIELDS, code -> false);

        assertEquals("138****1234", acc.mobile);          // MOBILE 脱敏
        assertEquals("z***@example.com", acc.email);      // EMAIL 脱敏
        assertNull(acc.username);                          // HIDE 置空
    }

    @Test
    void apply_withPermission_keepsPlaintext() {
        ColumnPermissionApplier applier = applierWithRegistry();
        Account acc = new Account("13800001234", "zhang@example.com", "alice");
        // 仅 mobile 拥有列权限
        Predicate<String> has = code -> code.equals("system:account:column:mobile");

        applier.apply(acc, "system:account", FIELDS, has);

        assertEquals("13800001234", acc.mobile);  // 有明文权，保持原文
        assertEquals("z***@example.com", acc.email);
        assertNull(acc.username);
    }

    @Test
    void apply_genericMask_whenSensitiveTypeNone() {
        ColumnPermissionApplier applier = applierWithRegistry();
        Account acc = new Account("12345678", "x@y.com", "bob");
        List<ColumnPermissionFieldView> generic = List.of(
                new ColumnPermissionFieldView("mobile", "手机号", ColumnStrategy.MASK, SensitiveType.NONE),
                new ColumnPermissionFieldView("email", "邮箱", ColumnStrategy.MASK, SensitiveType.EMAIL));
        applier.apply(acc, "system:account", generic, code -> false);
        // 首3末4、中间1个*：123*5678
        assertEquals("123*5678", acc.mobile);
        assertEquals("x***@y.com", acc.email);
    }

    @Test
    void applyAll_processesEachElement() {
        ColumnPermissionApplier applier = applierWithRegistry();
        Account a = new Account("13800001234", "a@b.com", "u1");
        Account b = new Account("13900001111", "c@d.com", "u2");
        List<Account> out = applier.applyAll(List.of(a, b), "system:account", FIELDS, code -> false);
        assertEquals(2, out.size());
        assertNotNull(out.get(0).mobile);
        assertEquals("138****1234", out.get(0).mobile);
        assertEquals("139****1111", out.get(1).mobile);
        assertNull(out.get(1).username);
    }

    @Test
    void apply_recordTarget_skipsImmutableFieldWithoutThrowing() {
        // record 不可变：执行器应跳过且不抛异常（仅记 WARN）
        record Profile(String mobile, String username) {
        }
        ColumnPermissionApplier applier = applierWithRegistry();
        Profile p = new Profile("13800001234", "alice");
        // 不抛异常即通过；record 字段无法写入，值保持原样
        applier.apply(p, "system:account", FIELDS, code -> false);
        assertEquals("13800001234", p.mobile());
    }
}
