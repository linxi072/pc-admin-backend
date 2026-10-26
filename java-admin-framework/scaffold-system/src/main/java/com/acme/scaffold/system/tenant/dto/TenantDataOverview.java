package com.acme.scaffold.system.tenant.dto;

import java.util.Map;

/**
 * 跨租户数据总览：单个租户在各业务表的行数快照。
 *
 * <p>{@code tableCounts} 的 key 为业务表名（见 {@code TenantTables#businessTableNames()}），
 * value 为该租户在该表的行数；{@code totalRows} 为所有业务表行数之和。</p>
 */
public record TenantDataOverview(Long tenantId, String tenantName, long totalRows,
                                Map<String, Long> tableCounts) {
}
