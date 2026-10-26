package com.acme.scaffold.system.tenant.dto;

import lombok.Data;

import java.util.List;

/**
 * 跨租户数据迁移请求：将指定业务表的数据从 {@code fromTenantId} 迁移到 {@code toTenantId}。
 *
 * <p>{@code tables} 为空表示迁移全部业务表；否则仅迁移列出的表。
 * 平台维表不在操作范围内。须在 {@code @TenantIgnorable} 跨租户旁路作用域下执行。</p>
 */
@Data
public class ReassignTenantRequest {

    /** 源租户 ID。 */
    private Long fromTenantId;

    /** 目标租户 ID。 */
    private Long toTenantId;

    /** 指定迁移的表名集合（空表示全部业务表）。 */
    private List<String> tables;
}
