package com.acme.scaffold.security.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultDataScopeProvider#resolve} 单测：以 mock 的 {@link DataScopeDataPort} 验证委托与合并裁决，
 * 不依赖数据库；回归锁定 B5 重构后 provider 仅依赖 Port、逻辑与历史行为一致。
 *
 * <p>P0-1 后默认兜底策略为 fail-closed（{@link DataScopeDefaultPolicy#SELF} 最小可见），
 * 故「无用户 / 无角色 / 无规则」三类场景断言从原 fail-open(ALL) 改为 fail-closed(SELF)。</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultDataScopeProviderResolveTest {

    @Mock
    private DataScopeDataPort port;

    @Mock
    private DataScopeProperties properties;

    @InjectMocks
    private DefaultDataScopeProvider provider;

    @BeforeEach
    void setUp() {
        // 默认兜底策略 SELF（fail-closed）：配置缺失即最小可见。
        // lenient：仅「无用户/无角色/无规则」三类用例会走到该兜底，其余用例（有角色且有规则）不会触达，
        // 用 lenient 避免 Mockito 严格 stubbing 将其判为多余 stub。
        lenient().when(properties.getDefaultPolicy()).thenReturn(DataScopeDefaultPolicy.SELF);
    }

    @Test
    void nullUserYieldsSelf() {
        assertTrue(provider.resolve(null, "res:x").selfOnly());
    }

    @Test
    void noRoleYieldsSelf() {
        when(port.roleIdsOfUser(1L)).thenReturn(Set.of());
        assertTrue(provider.resolve(1L, "res:x").selfOnly());
    }

    @Test
    void noScopeRuleYieldsSelf() {
        when(port.roleIdsOfUser(1L)).thenReturn(Set.of(5L));
        when(port.dataScopesOfRoles(Set.of(5L), "res:x")).thenReturn(Map.of());
        assertTrue(provider.resolve(1L, "res:x").selfOnly());
    }

    @Test
    void selfRuleYieldsSelf() {
        when(port.roleIdsOfUser(1L)).thenReturn(Set.of(5L));
        when(port.dataScopesOfRoles(Set.of(5L), "res:x")).thenReturn(Map.of(100L, "SELF"));
        assertTrue(provider.resolve(1L, "res:x").selfOnly());
    }

    @Test
    void deptAndChildResolvesChildOrgs() {
        when(port.roleIdsOfUser(1L)).thenReturn(Set.of(5L));
        when(port.dataScopesOfRoles(Set.of(5L), "res:x")).thenReturn(Map.of(100L, "DEPT_AND_CHILD"));
        when(port.primaryOrgIdOfUser(1L)).thenReturn(10L);
        when(port.childOrgIds(10L)).thenReturn(Set.of(11L, 12L));
        assertEquals(Set.of(10L, 11L, 12L), provider.resolve(1L, "res:x").orgIds());
    }

    @Test
    void customResolvesCustomOrgs() {
        when(port.roleIdsOfUser(1L)).thenReturn(Set.of(5L));
        when(port.dataScopesOfRoles(Set.of(5L), "res:x")).thenReturn(Map.of(100L, "CUSTOM"));
        when(port.primaryOrgIdOfUser(1L)).thenReturn(10L);
        when(port.customOrgIds(100L)).thenReturn(Set.of(20L, 21L));
        assertEquals(Set.of(20L, 21L), provider.resolve(1L, "res:x").orgIds());
    }

    @Test
    void allRuleWinsOverSelfAndOrg() {
        when(port.roleIdsOfUser(1L)).thenReturn(Set.of(5L, 6L));
        when(port.dataScopesOfRoles(Set.of(5L, 6L), "res:x"))
                .thenReturn(Map.of(100L, "SELF", 101L, "ALL", 102L, "DEPT"));
        assertTrue(provider.resolve(1L, "res:x").unrestricted());
    }
}
