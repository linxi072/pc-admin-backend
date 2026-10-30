package com.acme.scaffold.system.tenant.dto;

import com.acme.scaffold.system.tenant.domain.SysTenantDO;

import java.time.LocalDateTime;

/**
 * 租户视图（对应 {@code sys_tenant}）。
 *
 * @param id            租户记录 ID
 * @param tenantCode    租户编码（全局唯一）
 * @param tenantName    租户名称
 * @param contactName   联系人姓名
 * @param contactPhone  联系人电话
 * @param contactEmail  联系人邮箱
 * @param status        状态：ENABLED / DISABLED
 * @param expireAt      过期时间（可空）
 * @param remark        备注
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record TenantView(
        Long id,
        String tenantCode,
        String tenantName,
        String contactName,
        String contactPhone,
        String contactEmail,
        String status,
        LocalDateTime expireAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static TenantView of(SysTenantDO d) {
        if (d == null) {
            return null;
        }
        return new TenantView(
                d.getId(), d.getTenantCode(), d.getTenantName(), d.getContactName(),
                d.getContactPhone(), d.getContactEmail(), d.getStatus(), d.getExpireAt(),
                d.getRemark(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
