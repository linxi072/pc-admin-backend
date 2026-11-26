package com.acme.scaffold.common.tenant;

import java.util.Map;
import java.util.Set;

/**
 * 租户解析器（M19 解析基础设施，纯逻辑、无 Spring 依赖，可直接离线单测）。
 *
 * <p><b>解析优先级</b>：经授权的请求头覆盖 &gt; JWT/Token Claim &gt; 默认租户。
 * 请求头只有在调用方显式传入 {@code allowHeaderOverride=true} 时才参与解析；该标记必须由调用方
 * 在完成身份鉴别后给出（仅平台管理员可为 true），普通用户伪造头不会覆盖其 Token Claim，避免跨租户越权。
 * 头/Claim 取值经 {@link TenantRules#normalizeTenantId} 归一，非法值（负数/非数字）不静默接受，
 * 回落默认租户（由调用方记录告警）。</p>
 *
 * <p><b>开关</b>：{@code enabled=false} 时强制回落默认租户（隔离关闭，全部视为平台租户），
 * 便于灰度与回滚。<b>平台管理员</b>：当前角色集合包含 {@code platformAdminRoleCode} 即视为平台管理员作用域，
 * 后续 P3 据此开放跨租户通道（@TenantIgnorable）。</p>
 */
public final class TenantResolver {

    private TenantResolver() {
    }

    /**
     * 解析当前租户 ID。
     *
     * @param headerValue 请求头（如 X-Tenant-Id）原始值，可为 null
     * @param claims      JWT/Token 声明映射（含 tid 等），可为 null
     * @param props       租户配置（enabled / 默认租户 / claim 名）
     * @return 解析到的租户 ID（永不返回 null；隔离关闭或未解析到时回落默认租户）
     */
    public static long resolveTenantId(String headerValue, Map<String, Object> claims, TenantProperties props) {
        return resolveTenantId(headerValue, claims, props, false);
    }

    /**
     * 解析当前租户 ID（显式指定是否允许请求头覆盖）。
     *
     * @param headerValue          请求头（如 X-Tenant-Id）原始值，可为 null
     * @param claims               JWT/Token 声明映射（含 tid 等），可为 null
     * @param props                租户配置（enabled / 默认租户 / claim 名）
     * @param allowHeaderOverride  调用方是否已确认请求头覆盖权限；仅平台管理员可为 true
     * @return 解析到的租户 ID（永不返回 null；隔离关闭或未解析到时回落默认租户）
     */
    public static long resolveTenantId(String headerValue, Map<String, Object> claims, TenantProperties props,
                                       boolean allowHeaderOverride) {
        if (props == null || !props.isEnabled()) {
            return defaultTenantId(props);
        }
        Long fromHeader = allowHeaderOverride ? normalize(headerValue) : null;
        if (fromHeader != null) {
            return fromHeader;
        }
        Long fromClaim = resolveFromClaim(claims, props);
        if (fromClaim != null) {
            return fromClaim;
        }
        return defaultTenantId(props);
    }

    /**
     * 解析当前租户 ID（仅基于 Token Claim，无请求头场景——如异步任务、内部调用）。
     */
    public static long resolveTenantIdFromClaims(Map<String, Object> claims, TenantProperties props) {
        return resolveTenantId(null, claims, props);
    }

    /**
     * 是否平台管理员作用域（角色集合含配置的平台管理员角色码）。
     */
    public static boolean isPlatformAdmin(Set<String> roles, TenantProperties props) {
        if (roles == null || roles.isEmpty() || props == null || props.getPlatformAdminRoleCode() == null) {
            return false;
        }
        return roles.contains(props.getPlatformAdminRoleCode());
    }

    private static Long resolveFromClaim(Map<String, Object> claims, TenantProperties props) {
        if (claims == null || props.getClaimName() == null) {
            return null;
        }
        return normalize(claims.get(props.getClaimName()));
    }

    private static Long normalize(Object raw) {
        return TenantRules.normalizeTenantId(raw);
    }

    private static long defaultTenantId(TenantProperties props) {
        Long d = props == null ? null : props.getDefaultTenantId();
        return d == null ? TenantRules.PLATFORM_TENANT_ID : d;
    }
}
