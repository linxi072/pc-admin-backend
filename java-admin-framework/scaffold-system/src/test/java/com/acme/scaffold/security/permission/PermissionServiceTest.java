package com.acme.scaffold.security.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * PermissionService 编排逻辑黄金测试（B1 重构后）。
 *
 * <p>依赖 {@link PermissionDataPort} 接口（Mock），覆盖：空角色直接返回空集、
 * 权限码聚合（接口 + 菜单）、角色码聚合。适配器的真实 jOOQ 查询由 MySQL 集成测试覆盖，
 * 本测试离线仅验证编排契约不变。</p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionDataPort permissionDataPort;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    void getPermissions_noRoles_returnsEmpty() {
        when(permissionDataPort.roleIdsOfUser(1L)).thenReturn(Set.of());
        assertTrue(permissionService.getPermissions(1L).isEmpty());
    }

    @Test
    void getPermissions_aggregatesApiAndMenuCodes() {
        when(permissionDataPort.roleIdsOfUser(1L)).thenReturn(Set.of(10L, 20L));
        when(permissionDataPort.permissionCodesOfRoles(Set.of(10L, 20L)))
                .thenReturn(Set.of("system:user:read", "system:user:write"));
        assertEquals(Set.of("system:user:read", "system:user:write"), permissionService.getPermissions(1L));
    }

    @Test
    void getRoleCodes_noRoles_returnsEmpty() {
        when(permissionDataPort.roleIdsOfUser(2L)).thenReturn(Set.of());
        assertTrue(permissionService.getRoleCodes(2L).isEmpty());
    }

    @Test
    void getRoleCodes_returnsRoleCodes() {
        when(permissionDataPort.roleIdsOfUser(2L)).thenReturn(Set.of(5L));
        when(permissionDataPort.roleCodesOfRoles(Set.of(5L))).thenReturn(Set.of("ADMIN", "USER"));
        assertEquals(Set.of("ADMIN", "USER"), permissionService.getRoleCodes(2L));
    }
}
