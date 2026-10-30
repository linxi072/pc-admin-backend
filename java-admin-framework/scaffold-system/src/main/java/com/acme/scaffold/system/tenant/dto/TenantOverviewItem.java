package com.acme.scaffold.system.tenant.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 运营总览中的单个租户摘要：元数据（来自 {@code sys_tenant}）+ 数据量（来自跨租户数据概览）
 * + 用户规模（来自 {@code sys_user} 按 tenant_id 聚合）+ 过期预警。
 *
 * @param tenantId      租户记录 ID
 * @param tenantCode    租户编码
 * @param tenantName    租户名称
 * @param status        状态：ENABLED / DISABLED
 * @param expireAt      过期时间（可空）
 * @param totalRows     该租户在全部业务表的行数合计（来自 {@link TenantDataOverview}）
 * @param userCount     该租户用户数（来自 sys_user 按 tenant_id 聚合，未删除）
 * @param expiringSoon  是否即将过期（expireAt 非空且距今 30 天内）
 * @param tableCounts   各业务表行数明细（key=表名，value=行数），供展开查看
 */
public record TenantOverviewItem(
        Long tenantId,
        String tenantCode,
        String tenantName,
        String status,
        LocalDateTime expireAt,
        long totalRows,
        long userCount,
        boolean expiringSoon,
        Map<String, Long> tableCounts) {
}
