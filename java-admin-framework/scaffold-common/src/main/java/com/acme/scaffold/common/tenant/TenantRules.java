package com.acme.scaffold.common.tenant;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户相关纯函数规则：租户 ID 解析/归一/校验、平台租户判定、租户编码/名称结构校验。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link TenantResolver}（请求级解析）、P2 的 {@code TenantService}（管理写入校验）、
 * P3 的 jOOQ 强制注入（越权拦截）复用，保证「存什么、查什么、按什么隔离」三处口径同源。</p>
 */
public final class TenantRules {

    /** 平台租户 ID（共享超级租户，业务数据隔离的「0 号租户」，由平台管理员持有）。 */
    public static final long PLATFORM_TENANT_ID = 0L;

    /** 租户编码正则：大写字母/数字/下划线/连字符，3–64 位。 */
    public static final String TENANT_CODE_PATTERN = "^[A-Z0-9_-]{3,64}$";

    private TenantRules() {
    }

    // ------------------------------------------------------------------
    // 租户 ID 解析与校验
    // ------------------------------------------------------------------

    /** 租户 ID 合法性：非 null 且 >= 0（0 为平台租户）。 */
    public static boolean isValidTenantId(Long id) {
        return id != null && id >= 0;
    }

    /** 是否为平台租户（id == 0）。 */
    public static boolean isPlatformTenant(Long id) {
        return id != null && id == PLATFORM_TENANT_ID;
    }

    /**
     * 归一化租户 ID：接受数字字符串 / Number；解析为非负 long，否则返回 null（调用方据此拒绝）。
     * 负数、空串、非数字串一律返回 null（不静默回落），由调用方决定是拒绝还是使用默认租户。
     */
    public static Long normalizeTenantId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            long v = n.longValue();
            return v >= 0 ? v : null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            long v = Long.parseLong(s);
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 租户编码/名称结构校验（P2 创建/更新租户用）
    // ------------------------------------------------------------------

    /** 租户编码合法性：大写字母/数字/下划线/连字符，3–64 位。 */
    public static boolean isValidTenantCode(String code) {
        return code != null && code.matches(TENANT_CODE_PATTERN);
    }

    /** 租户名称合法性：trim 后 1–128 字符。 */
    public static boolean isValidTenantName(String name) {
        if (name == null) {
            return false;
        }
        String t = name.trim();
        return t.length() >= 1 && t.length() <= 128;
    }

    /**
     * 全字段结构校验（创建/部分更新合并后统一调用）。返回错误列表，空列表表示通过。
     * 仅做结构校验（编码/名称格式），唯一性由 Service 查库裁决。
     */
    public static List<String> validateTenant(String tenantCode, String tenantName) {
        List<String> errors = new ArrayList<>();
        if (!isValidTenantCode(tenantCode)) {
            errors.add("租户编码须为 3–64 位大写字母/数字/下划线/连字符");
        }
        if (!isValidTenantName(tenantName)) {
            errors.add("租户名称须为 1–128 字符");
        }
        return errors;
    }
}
