package com.acme.scaffold.security.permission;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultDataScopeProvider#mergeRules} 纯函数单测：覆盖纯 N:N（用户多角色）下的数据范围合并裁决。
 * 不依赖数据库，可在沙箱离线运行；回归锁定「多角色规则先收集再统一裁决、避免单角色短路漏判」的修复。
 */
class DefaultDataScopeProviderTest {

    private final DefaultDataScopeProvider provider = new DefaultDataScopeProvider(null);

    private DataScopeResult merge(DataScopeType... types) {
        return provider.mergeRules(Set.of(1L, 2L), 10L, List.of(types), Set.of(), "res:x");
    }

    @Test
    void singleAllYieldsUnrestricted() {
        assertTrue(merge(DataScopeType.ALL).unrestricted());
    }

    @Test
    void singleSelfYieldsSelfOnly() {
        DataScopeResult r = merge(DataScopeType.SELF);
        assertTrue(r.selfOnly());
        assertEquals(DataScopeType.SELF, r.type());
    }

    @Test
    void allWinsOverSelfAndOrg() {
        // 任一角色 ALL 即整体不限（最高优先级），不受其他角色 SELF/DEPT 影响
        assertTrue(merge(DataScopeType.SELF, DataScopeType.ALL, DataScopeType.DEPT).unrestricted());
    }

    @Test
    void selfWinsOverOrgRule() {
        // 回归点：旧实现若先处理 DEPT 会提前 return DEPT 范围而漏掉另一角色的 SELF。
        // 纯 N:N 下 SELF 应优先于机构类规则（本人优先于机构）。
        DataScopeResult r = merge(DataScopeType.DEPT, DataScopeType.SELF);
        assertTrue(r.selfOnly(), "SELF 应优先于 DEPT");
    }

    @Test
    void orgRulesUnionWhenNoAllOrSelf() {
        // DEPT 与 DEPT_AND_CHILD 并集（resolvedOrgIds 已由调用方解析，此处直接传入并集）
        DataScopeResult r = provider.mergeRules(
                Set.of(1L), 10L,
                List.of(DataScopeType.DEPT, DataScopeType.DEPT_AND_CHILD),
                Set.of(10L, 11L, 12L), "res:x");
        assertEquals(DataScopeType.CUSTOM, r.type());
        assertEquals(Set.of(10L, 11L, 12L), r.orgIds());
        assertTrue(r.unrestricted() || !r.selfOnly());
    }

    @Test
    void orgRuleWithEmptyResolvedDegradesToAll() {
        // 存在机构类规则却解析不出任何机构（如用户未挂部门）：fail-open 降级为不限并告警
        DataScopeResult r = provider.mergeRules(
                Set.of(1L), null,
                List.of(DataScopeType.DEPT), Set.of(), "res:x");
        assertTrue(r.unrestricted(), "机构类规则解析为空应 fail-open 为不限");
    }

    @Test
    void customRuleWithEmptyResolvedDegradesToAll() {
        DataScopeResult r = provider.mergeRules(
                Set.of(1L), 10L,
                List.of(DataScopeType.CUSTOM), Set.of(), "res:x");
        assertTrue(r.unrestricted(), "CUSTOM 规则无机构应 fail-open 为不限");
    }

    @Test
    void mixedDeptAndCustomUnion() {
        DataScopeResult r = provider.mergeRules(
                Set.of(1L, 2L), 10L,
                List.of(DataScopeType.DEPT, DataScopeType.CUSTOM),
                Set.of(10L, 20L), "res:x");
        assertEquals(Set.of(10L, 20L), r.orgIds());
    }
}
