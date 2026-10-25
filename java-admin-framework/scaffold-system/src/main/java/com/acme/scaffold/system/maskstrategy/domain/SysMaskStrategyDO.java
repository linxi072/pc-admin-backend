package com.acme.scaffold.system.maskstrategy.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脱敏策略实体（对应 {@code sys_mask_strategy}，DDL 见 V46__mask_strategy_center.sql）。
 *
 * <p><b>收口语义</b>：把「某逻辑字段用哪种脱敏类型、对哪些角色豁免」从散落各处的硬编码
 * 收口为可配置策略，统一经 {@code MaskStrategyResolver} 委托 {@code SensitiveMasker} 分派。
 * 未命中任何策略的字段按调用方声明的 {@code SensitiveType} 默认掩码（安全回落，绝不泄漏明文）。</p>
 *
 * <p><b>唯一键</b>：{@code uk_tenant_code} 保证同一租户内策略编码唯一；写入走「查现存 → 更新或插入」，
 * 唯一索引兜底并发（与幂等记录表同范式）。</p>
 */
@Data
public class SysMaskStrategyDO {

    private Long id;

    private Long tenantId = 0L;

    /** 策略编码（租户内唯一，如 USER_MOBILE）。 */
    private String strategyCode;

    /** 策略名称（展示用）。 */
    private String strategyName;

    /** 逻辑字段键（解析/命中标识，如 sys_user.mobile / audit.ip）。 */
    private String fieldKey;

    /** 脱敏类型：SensitiveType 枚举大写（MOBILE/ID_CARD/BANK_CARD/EMAIL/NAME/ADDRESS/IP/CREDENTIAL_TEXT）。 */
    private String sensitiveType;

    /** 作用范围：ALL=一律脱敏；ROLE_EXEMPT=豁免角色看明文；NONE=永不脱敏。 */
    private String applyScope;

    /** 豁免角色列表（逗号/分号分隔的 role_code；仅 ROLE_EXEMPT 生效）。 */
    private String exemptRoles;

    /** 优先级（数值越大越优先命中）。 */
    private Integer priority;

    /** 是否启用：1=启用，0=停用。 */
    private Integer enabled;

    /** 备注。 */
    private String remark;

    private Long version;

    private Integer deleted;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
