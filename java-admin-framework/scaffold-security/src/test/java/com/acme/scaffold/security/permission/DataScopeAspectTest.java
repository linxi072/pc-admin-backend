package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeAspect} 单测：锁定 D1 修复后的「超管放行」与「普通角色走规则解析」两条分支。
 *
 * <p>背景：种子数据未预置 {@code sys_role_data_scope} 规则，默认兜底策略 SELF 会让超管只可见自己，
 * 表现为新建用户成功（200）但列表为空、详情 404。修复后超管直接按「不限制」处理，
 * 普通角色仍按 provider 解析（fail-closed 语义不变）。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeAspectTest {

    private static final String RESOURCE = "system:user";

    @Mock
    private DataScopeProvider dataScopeProvider;

    @Mock
    private SecurityContextFacade securityContextFacade;

    private DataScopeProperties properties;

    private DataScopeAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new DataScopeProperties();
        aspect = new DataScopeAspect(dataScopeProvider, securityContextFacade, properties);
    }

    @Test
    void superAdminBypassesRuleResolution() {
        when(securityContextFacade.getCurrentPrincipal())
                .thenReturn(Optional.of(principal(Set.of("SUPER_ADMIN"))));

        DataScopeResult result = aspect.resolve(RESOURCE);

        assertTrue(result.unrestricted(), "超管应获不限制范围");
        verify(dataScopeProvider, never()).resolve(anyLong(), anyString());
    }

    @Test
    void superAdminBypassHonoursBypassSwitch() {
        properties.setSuperAdminBypass(false);
        when(securityContextFacade.getCurrentPrincipal())
                .thenReturn(Optional.of(principal(Set.of("SUPER_ADMIN"))));
        when(dataScopeProvider.resolve(1L, RESOURCE)).thenReturn(DataScopeResult.self());

        DataScopeResult result = aspect.resolve(RESOURCE);

        assertTrue(result.selfOnly(), "关闭放行开关后超管同样走规则解析");
        verify(dataScopeProvider).resolve(1L, RESOURCE);
    }

    @Test
    void normalRoleKeepsRuleResolution() {
        when(securityContextFacade.getCurrentPrincipal())
                .thenReturn(Optional.of(principal(Set.of("DEPT_MANAGER"))));
        when(dataScopeProvider.resolve(1L, RESOURCE)).thenReturn(DataScopeResult.ofOrgs(Set.of(10L)));

        DataScopeResult result = aspect.resolve(RESOURCE);

        assertEquals(Set.of(10L), result.orgIds(), "普通角色仍按角色规则解析");
        verify(dataScopeProvider).resolve(1L, RESOURCE);
    }

    @Test
    void anonymousKeepsUnrestricted() {
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());

        assertTrue(aspect.resolve(RESOURCE).unrestricted(), "无登录主体（定时任务/内部调用）按不限制处理");
        verify(dataScopeProvider, never()).resolve(anyLong(), anyString());
    }

    private CurrentPrincipal principal(Set<String> roles) {
        return new CurrentPrincipal(1L, "admin", "管理员", 0L, "web", 0, roles, Set.of("system:user:read"));
    }
}
