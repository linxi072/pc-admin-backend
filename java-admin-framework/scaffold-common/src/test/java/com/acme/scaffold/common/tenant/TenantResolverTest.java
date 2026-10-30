package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantResolver} 离线单测：解析优先级（请求头 > Claim > 默认）、隔离开关、非法值回落、
 * 平台管理员判定。纯逻辑，不依赖 Spring 上下文。
 */
class TenantResolverTest {

    private TenantProperties props() {
        return new TenantProperties();   // 默认 enabled=true, default=0, claimName=tid
    }

    // ------------------------------------------------------------------
    // 解析优先级
    // ------------------------------------------------------------------

    @Test
    @DisplayName("resolveTenantId：请求头优先于 Claim，Claim 优先于默认")
    void resolve_priority() {
        TenantProperties p = props();
        Map<String, Object> claims = Map.of("tid", 5);

        // 头 + claim 同时存在 → 头胜
        assertEquals(9L, TenantResolver.resolveTenantId("9", claims, p));
        // 仅 claim → claim 胜
        assertEquals(5L, TenantResolver.resolveTenantId(null, claims, p));
        // 均无 → 默认（平台租户 0）
        assertEquals(0L, TenantResolver.resolveTenantId(null, null, p));
    }

    @Test
    @DisplayName("resolveTenantId：自定义默认租户与 claim 名生效")
    void resolve_customConfig() {
        TenantProperties p = props();
        p.setDefaultTenantId(100L);
        p.setClaimName("tenant_id");
        Map<String, Object> claims = Map.of("tenant_id", 7);
        assertEquals(7L, TenantResolver.resolveTenantId(null, claims, p));
        assertEquals(100L, TenantResolver.resolveTenantId(null, Map.of(), p));
    }

    // ------------------------------------------------------------------
    // 开关与非法值回落
    // ------------------------------------------------------------------

    @Test
    @DisplayName("resolveTenantId：enabled=false 强制回落默认租户")
    void resolve_disabledForcesDefault() {
        TenantProperties p = props();
        p.setEnabled(false);
        p.setDefaultTenantId(0L);
        assertEquals(0L, TenantResolver.resolveTenantId("9", Map.of("tid", 5), p));
    }

    @Test
    @DisplayName("resolveTenantId：非法头/Claim 值不静默接受，回落默认")
    void resolve_invalidFallsBack() {
        TenantProperties p = props();
        // 负数 / 非数字 → 归一为 null → 回落默认
        assertEquals(0L, TenantResolver.resolveTenantId("-3", Map.of("tid", "abc"), p));
    }

    @Test
    @DisplayName("resolveTenantId：props 为 null 时回落平台租户")
    void resolve_nullProps() {
        assertEquals(0L, TenantResolver.resolveTenantId("9", Map.of("tid", 5), null));
    }

    // ------------------------------------------------------------------
    // 平台管理员
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isPlatformAdmin：角色集合含配置角色码即判定为平台管理员")
    void isPlatformAdmin() {
        TenantProperties p = props();   // platformAdminRoleCode = PLATFORM_ADMIN
        assertTrue(TenantResolver.isPlatformAdmin(Set.of("PLATFORM_ADMIN"), p));
        assertTrue(TenantResolver.isPlatformAdmin(Set.of("OPERATOR", "PLATFORM_ADMIN"), p));
        assertFalse(TenantResolver.isPlatformAdmin(Set.of("OPERATOR"), p));
        assertFalse(TenantResolver.isPlatformAdmin(Set.of(), p));
        assertFalse(TenantResolver.isPlatformAdmin(null, p));
        assertFalse(TenantResolver.isPlatformAdmin(Set.of("PLATFORM_ADMIN"), null));
    }
}
