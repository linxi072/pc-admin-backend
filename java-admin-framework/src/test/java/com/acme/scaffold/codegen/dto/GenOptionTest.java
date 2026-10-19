package com.acme.scaffold.codegen.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GenOption} 入参校验单测。
 *
 * <p>2026-10-19 测试轮次修复：{@code className} 未做白名单校验即拼入 zip 条目路径，
 * {@code "../../x"} 可生成含 {@code ../} 的条目（Zip Slip）。现在构造期拒绝。</p>
 */
class GenOptionTest {

    @Test
    @DisplayName("合法 className 通过")
    void validClassName_passes() {
        assertDoesNotThrow(() -> new GenOption("sys_user", "user", "用户管理", "User", "admin", true));
    }

    @Test
    @DisplayName("className 为空/null 允许（由表名自动推导）")
    void blankClassName_passes() {
        assertDoesNotThrow(() -> new GenOption("sys_user", "user", null, null, null, false));
        assertDoesNotThrow(() -> new GenOption("sys_user", "user", null, "  ", null, false));
    }

    @Test
    @DisplayName("className 含目录穿越字符（Zip Slip）：构造期拒绝")
    void pathTraversalClassName_rejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new GenOption("sys_user", "user", null, "../../evil", null, false));
        assertTrue(ex.getMessage().contains("className"), "实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("className 以数字/特殊字符开头：构造期拒绝")
    void invalidStartClassName_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new GenOption("sys_user", "user", null, "1User", null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new GenOption("sys_user", "user", null, "User$", null, false));
    }

    @Test
    @DisplayName("既有 moduleName 规则保持有效")
    void moduleNameRules_preserved() {
        assertDoesNotThrow(() -> new GenOption("sys_user", "user-manage", null, "User", null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new GenOption("sys_user", "User", null, "User", null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new GenOption("sys_user", "../user", null, "User", null, false));
    }
}
