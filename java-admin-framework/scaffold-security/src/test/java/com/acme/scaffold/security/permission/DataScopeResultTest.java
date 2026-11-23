package com.acme.scaffold.security.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 数据权限结果纯规则单测（TEST-COVERAGE-PLAN 第二批 / T1）。
 * 锁定 fail-closed 兜底与机构集合不可变契约（P0-1 整改核心）。
 */
class DataScopeResultTest {

    @Test
    void factoriesShouldSetCorrectType() {
        assertTrue(DataScopeResult.all().unrestricted());
        assertFalse(DataScopeResult.self().unrestricted());
        assertTrue(DataScopeResult.self().selfOnly());
        assertFalse(DataScopeResult.all().selfOnly());
    }

    @Test
    void ofPolicyShouldDefaultToSelfForFailClosed() {
        assertTrue(DataScopeResult.ofPolicy(DataScopeDefaultPolicy.SELF).selfOnly(),
                "SELF 兜底是 P0-1 fail-closed 的核心，禁止回退");
        assertTrue(DataScopeResult.ofPolicy(DataScopeDefaultPolicy.ALL).unrestricted());
    }

    @Test
    void ofOrgsShouldCreateCustomWithImmutableCopy() {
        Set<Long> input = new LinkedHashSet<>(List.of(1L, 2L));
        DataScopeResult result = DataScopeResult.ofOrgs(input);
        assertEquals(DataScopeType.CUSTOM, result.type());
        assertEquals(Set.of(1L, 2L), result.orgIds());
        assertThrows(UnsupportedOperationException.class, () -> result.orgIds().add(3L),
                "返回的集合不可变，防止调用方篡改权限范围");
        input.add(3L);
        assertEquals(Set.of(1L, 2L), result.orgIds(), "应是副本，不受外部集合变更影响");
    }

    @Test
    void ofOrgsShouldDowngradeToAllOnEmptyInput() {
        assertTrue(DataScopeResult.ofOrgs(null).unrestricted());
        assertTrue(DataScopeResult.ofOrgs(Set.of()).unrestricted(),
                "配置机构规则但集合为空时降级 ALL，避免查不到任何数据");
    }

    @Test
    void selfResultShouldHaveEmptyOrgIds() {
        assertTrue(DataScopeResult.self().orgIds().isEmpty(), "SELF 按本人过滤，机构集合无意义");
    }
}
