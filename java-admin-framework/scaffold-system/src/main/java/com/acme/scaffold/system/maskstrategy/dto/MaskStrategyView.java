package com.acme.scaffold.system.maskstrategy.dto;

import com.acme.scaffold.system.maskstrategy.domain.SysMaskStrategyDO;

import java.time.LocalDateTime;

/**
 * 脱敏策略视图（对应 {@code sys_mask_strategy}）。
 *
 * <p>{@code enabled} 由整型 0/1 映射为布尔；{@code exemptRoles} 原样透传（前端按逗号/分号拆分展示）。</p>
 *
 * @param id            策略 ID
 * @param strategyCode  策略编码（租户内唯一）
 * @param strategyName  策略名称
 * @param fieldKey      逻辑字段键
 * @param sensitiveType 脱敏类型（SensitiveType 枚举名）
 * @param applyScope    作用范围：ALL / ROLE_EXEMPT / NONE
 * @param exemptRoles   豁免角色列表（逗号/分号分隔）
 * @param priority      优先级（数值越大越优先）
 * @param enabled       是否启用
 * @param remark        备注
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record MaskStrategyView(
        Long id,
        String strategyCode,
        String strategyName,
        String fieldKey,
        String sensitiveType,
        String applyScope,
        String exemptRoles,
        int priority,
        boolean enabled,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static MaskStrategyView of(SysMaskStrategyDO d) {
        if (d == null) {
            return null;
        }
        return new MaskStrategyView(
                d.getId(), d.getStrategyCode(), d.getStrategyName(), d.getFieldKey(),
                d.getSensitiveType(), d.getApplyScope(), d.getExemptRoles(),
                d.getPriority() == null ? 0 : d.getPriority(),
                d.getEnabled() != null && d.getEnabled() == 1,
                d.getRemark(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
