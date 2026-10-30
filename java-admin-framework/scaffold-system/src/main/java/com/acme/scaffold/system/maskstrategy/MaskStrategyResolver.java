package com.acme.scaffold.system.maskstrategy;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.system.maskstrategy.domain.SysMaskStrategyDO;
import com.acme.scaffold.system.maskstrategy.rules.MaskStrategyRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 脱敏策略解析器（V46 脱敏策略中心统一分派入口）。
 *
 * <p><b>职责</b>：序列化 / 导出前，按 {@code fieldKey} 命中策略中心的配置，决定「脱敏还是明文、
 * 用哪种算法」，最终委托 {@link SensitiveMasker}（与 M1 审计落库、M20 审计增强共用的脱敏内核）。</p>
 *
 * <p><b>默认安全回落</b>：未命中任何策略时，按调用方声明的 {@code fallbackType} 默认掩码——
 * 即与「未引入策略中心时、视图层自行声明的类型驱动掩码」口径完全一致，绝不因「未配置策略」而泄漏明文；
 * {@code fallbackType = NONE} 时原样返回（与既有「本就不脱敏」语义一致）。命中策略但命中角色豁免时返回明文。</p>
 *
 * <p><b>线程安全</b>：无状态，依赖 {@link MaskStrategyService} 的按租户只读缓存，可并发调用。</p>
 */
@Component
@RequiredArgsConstructor
public class MaskStrategyResolver {

    private final MaskStrategyService service;

    /**
     * 解析单个值的脱敏结果（显式上下文）。
     *
     * @param tenantId     租户 ID（策略按租户隔离；平台级默认策略通常挂在租户 0）
     * @param fieldKey     逻辑字段键（如 sys_user.mobile / audit.ip），用于命中策略
     * @param value        原始值（null 原样返回）
     * @param fallbackType 未命中策略时的兜底脱敏类型（null 视为 NONE = 不脱敏）
     * @param roles        当前调用方角色集合（用于 ROLE_EXEMPT 豁免判定；null/空视为无豁免）
     * @return 脱敏后的值；命中豁免或兜底为 NONE 时可能返回原文
     */
    public String resolve(long tenantId, String fieldKey, String value,
                          SensitiveType fallbackType, Set<String> roles) {
        if (value == null) {
            return null;
        }
        SysMaskStrategyDO strategy = MaskStrategyRules.selectStrategy(
                service.activeStrategies(tenantId), fieldKey);
        if (strategy == null) {
            // 默认安全回落：沿用调用方声明的类型驱动默认掩码
            return SensitiveMasker.mask(value, fallbackType == null ? SensitiveType.NONE : fallbackType);
        }
        if (!MaskStrategyRules.shouldMask(strategy, roles)) {
            // 命中角色豁免（如特权角色）→ 明文
            return value;
        }
        return SensitiveMasker.mask(value, MaskStrategyRules.decideType(strategy));
    }

    /**
     * 按当前登录用户解析（从安全上下文取租户与角色）。无登录主体时回落平台租户(0)且无豁免角色，
     * 与「系统/异步导出」等无主体场景兼容（不抛异常）。
     */
    public String resolveForCurrentUser(SecurityContextFacade facade, String fieldKey, String value,
                                        SensitiveType fallbackType) {
        CurrentPrincipal principal = facade.getCurrentPrincipal().orElse(null);
        long tenantId = principal == null ? 0L : (principal.tenantId() == null ? 0L : principal.tenantId());
        Set<String> roles = principal == null ? Set.of() : (principal.roles() == null ? Set.of() : principal.roles());
        return resolve(tenantId, fieldKey, value, fallbackType, roles);
    }
}
