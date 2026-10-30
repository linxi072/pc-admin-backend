package com.acme.scaffold.system.tenant.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体（对应 {@code sys_tenant}，V51 租户管理域 / M19 P2）。
 *
 * <p>该表为「租户维度表」而非「被隔离数据」：所有行 {@code tenantId} 恒为 0（平台租户），
 * 由平台管理员统一维护，查询不按当前租户过滤（见 {@code TenantService}）。</p>
 */
@Data
public class SysTenantDO {

    private Long id;

    /**
     * 租户 ID（恒为 0，平台租户——租户维度表由平台统一维护）。
     * <p>无默认值：插入由 JooqWriters / 迁移的 {@code DEFAULT 0} 兜底，
     * 更新补丁对象（{@code new SysTenantDO()}）保持为 null 以免误写该列。</p>
     */
    private Long tenantId;

    /** 租户编码（全局唯一，外部系统按此标识租户）。 */
    private String tenantCode;

    /** 租户名称（人类可读）。 */
    private String tenantName;

    /** 联系人姓名。 */
    private String contactName;

    /** 联系人电话。 */
    private String contactPhone;

    /** 联系人邮箱。 */
    private String contactEmail;

    /** 状态：ENABLED / DISABLED。 */
    private String status;

    /** 可选过期时间（到期后应停用，具体拦截策略待 P3 隔离落地）。 */
    private LocalDateTime expireAt;

    private String remark;

    /** 乐观锁版本。 */
    private Long version;

    private Integer deleted;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
