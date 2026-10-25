package com.acme.scaffold.system.maskstrategy.rules;

import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.system.maskstrategy.domain.SysMaskStrategyDO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 脱敏策略纯函数规则：脱敏类型/作用范围归一、豁免角色解析、按字段键命中（优先级最高者胜）、
 * 角色豁免判定与最终类型裁决。
 *
 * <p>无副作用、不依赖 Spring 上下文与数据库，可直接离线单测；供
 * {@link com.acme.scaffold.system.maskstrategy.MaskStrategyResolver}（序列化/导出前分派）、
 * {@link com.acme.scaffold.system.maskstrategy.MaskStrategyService}（管理写入校验）复用，
 * 保证「存什么、查什么、按什么放行」三处口径同源。</p>
 *
 * <p><b>作用范围</b>：
 * <ul>
 *   <li>{@code ALL}：对所有调用方脱敏（最严格，如导出文件离开系统边界）；</li>
 *   <li>{@code ROLE_EXEMPT}：命中 {@code exempt_roles} 的角色看明文，其余脱敏——管理面「按角色可见明文」；</li>
 *   <li>{@code NONE}：永不脱敏（显式放行，用于已脱敏存储字段，避免二次脱敏）。</li>
 * </ul>
 * 实际脱敏算法由 {@code sensitive_type} 驱动，委托 {@code SensitiveMasker.mask(value, type)}。</p>
 */
public final class MaskStrategyRules {

    /** 可选脱敏类型（与 SensitiveType 枚举一致，NONE 仅作为显式放行标志，不在下拉可选之列）。 */
    public static final List<SensitiveType> SELECTABLE_TYPES = List.of(
            SensitiveType.MOBILE, SensitiveType.ID_CARD, SensitiveType.BANK_CARD, SensitiveType.EMAIL,
            SensitiveType.NAME, SensitiveType.ADDRESS, SensitiveType.IP, SensitiveType.CREDENTIAL_TEXT);

    /** 一律脱敏。 */
    public static final String SCOPE_ALL = "ALL";
    /** 命中豁免角色看明文，其余脱敏。 */
    public static final String SCOPE_ROLE = "ROLE_EXEMPT";
    /** 永不脱敏（显式放行）。 */
    public static final String SCOPE_NONE = "NONE";

    private static final Set<String> SCOPES = Set.of(SCOPE_ALL, SCOPE_ROLE, SCOPE_NONE);

    /** 默认作用范围（编码缺省时）。 */
    public static final String DEFAULT_SCOPE = SCOPE_ROLE;

    /** 优先级上限（防止异常大值导致排序不稳定）。 */
    public static final int MAX_PRIORITY = 9999;

    private MaskStrategyRules() {
    }

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    /** 解析脱敏类型：合法枚举名（大小写不敏感）返回规范大写名，否则 null（调用方据此拒绝写入）。 */
    public static String normalizeSensitiveType(String sensitiveType) {
        SensitiveType t = parseSensitiveType(sensitiveType);
        return t == null ? null : t.name();
    }

    /** 解析脱敏类型枚举（null/非法返回 null）。 */
    public static SensitiveType parseSensitiveType(String sensitiveType) {
        if (sensitiveType == null || sensitiveType.isBlank()) {
            return null;
        }
        try {
            return SensitiveType.valueOf(sensitiveType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 归一作用范围：白名单内（大小写不敏感）返回规范大写，否则 null。 */
    public static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        String s = scope.trim().toUpperCase(Locale.ROOT);
        return SCOPES.contains(s) ? s : null;
    }

    /** 解析豁免角色：逗号/分号分隔的 role_code 去空白去重（保留声明顺序）；空串返回空集合。 */
    public static Set<String> parseExemptRoles(String exemptRoles) {
        if (exemptRoles == null || exemptRoles.isBlank()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : exemptRoles.split("[,;]")) {
            String r = part.trim();
            if (!r.isEmpty()) {
                out.add(r);
            }
        }
        return Set.copyOf(out);
    }

    // ------------------------------------------------------------------
    // 命中与裁决
    // ------------------------------------------------------------------

    /**
     * 按字段键命中策略：返回匹配 fieldKey 且启用中的、优先级数值最高的一条；无匹配返回 null。
     * 同优先级时取首条（遍历顺序稳定），避免随机抖动。
     */
    public static SysMaskStrategyDO selectStrategy(List<SysMaskStrategyDO> strategies, String fieldKey) {
        if (strategies == null || fieldKey == null) {
            return null;
        }
        SysMaskStrategyDO best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (SysMaskStrategyDO s : strategies) {
            if (s.getFieldKey() == null || !s.getFieldKey().equals(fieldKey)) {
                continue;
            }
            if (s.getEnabled() == null || s.getEnabled() != 1) {
                continue;
            }
            int p = s.getPriority() == null ? 0 : s.getPriority();
            if (best == null || p > bestPriority) {
                best = s;
                bestPriority = p;
            }
        }
        return best;
    }

    /**
     * 是否应脱敏（基于命中策略与作用范围/角色）。
     * ALL→脱敏；NONE→不脱敏；ROLE_EXEMPT→豁免角色命中则不脱敏，否则脱敏
     * （未配置豁免角色时按「脱敏」兜底，避免被误判为放行）。
     *
     * @param roles 当前调用方角色集合（null/空视为无豁免）
     */
    public static boolean shouldMask(SysMaskStrategyDO strategy, Set<String> roles) {
        String scope = strategy.getApplyScope();
        if (SCOPE_NONE.equals(scope)) {
            return false;
        }
        if (SCOPE_ALL.equals(scope)) {
            return true;
        }
        Set<String> exempt = parseExemptRoles(strategy.getExemptRoles());
        if (exempt.isEmpty()) {
            return true;
        }
        if (roles == null || roles.isEmpty()) {
            return true;
        }
        for (String r : roles) {
            if (exempt.contains(r)) {
                return false;
            }
        }
        return true;
    }

    /** 命中策略的最终脱敏类型（脱敏算法由该类型驱动）；非法类型回落 NONE（不脱敏，不抛异常）。 */
    public static SensitiveType decideType(SysMaskStrategyDO strategy) {
        SensitiveType t = parseSensitiveType(strategy.getSensitiveType());
        return t == null ? SensitiveType.NONE : t;
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 全字段结构校验（创建/部分更新合并后统一调用）。返回错误列表，空列表表示通过。
     * 仅做结构校验（编码/名称/字段键长度、脱敏类型/作用范围合法性、豁免长度、优先级范围），
     * 编码唯一性由 Service 查库裁决。
     */
    public static List<String> validate(String strategyCode, String strategyName, String fieldKey,
                                        String sensitiveType, String applyScope, String exemptRoles,
                                        Integer priority) {
        List<String> errors = new ArrayList<>();
        if (strategyCode == null || strategyCode.isBlank() || strategyCode.length() > 64) {
            errors.add("策略编码必填且不超过 64 字符");
        }
        if (strategyName == null || strategyName.isBlank() || strategyName.length() > 128) {
            errors.add("策略名称必填且不超过 128 字符");
        }
        if (fieldKey == null || fieldKey.isBlank() || fieldKey.length() > 128) {
            errors.add("字段键必填且不超过 128 字符");
        }
        if (normalizeSensitiveType(sensitiveType) == null) {
            errors.add("脱敏类型非法（须为 MOBILE/ID_CARD/BANK_CARD/EMAIL/NAME/ADDRESS/IP/CREDENTIAL_TEXT）");
        }
        if (normalizeScope(applyScope) == null) {
            errors.add("作用范围非法（须为 ALL/ROLE_EXEMPT/NONE）");
        }
        if (exemptRoles != null && exemptRoles.length() > 512) {
            errors.add("豁免角色列表过长（不超过 512 字符）");
        }
        if (priority != null && (priority < 0 || priority > MAX_PRIORITY)) {
            errors.add("优先级须在 0~" + MAX_PRIORITY + " 之间");
        }
        return errors;
    }
}
