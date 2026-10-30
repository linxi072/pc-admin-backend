package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色字段级（列级）数据权限分配记录。
 *
 * <p>与 {@link SysRoleDataScopeDO} 对称：行级数据权限以「角色 × 资源 → 范围类型」表达，
 * 列级数据权限以「角色 × 资源 × 字段 → 明文可见权」表达。一条记录代表「某角色对某资源的某字段拥有明文可见权」，
 * 不存在记录即视为该字段对该角色脱敏/隐藏（由运行时 {@code ColumnPermissionApplier} 统一裁决）。</p>
 */
@Data
public class SysRoleColumnPermissionDO {

    private Long id;

    private Long tenantId = 0L;
    private Long roleId;
    private String resourceCode;
    private String field;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
