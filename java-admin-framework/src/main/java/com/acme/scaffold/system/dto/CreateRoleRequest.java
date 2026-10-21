package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * 角色创建 / 更新请求（create 与 update 复用同一 DTO）。
 *
 * <p><b>绑定字段语义</b>（menuIds / apiIds）：
 * <ul>
 *   <li>{@code null}（不传）：create 时表示「不授权」；update 时表示「保持原绑定不变」；</li>
 *   <li>空集合：清空该角色的全部绑定；</li>
 *   <li>非空集合：以全量替换的方式重建绑定。</li>
 * </ul>
 * update 场景下 null 与空集合的区分是刻意设计：调用方局部更新角色信息时
 * 不应因漏传绑定字段而丢失全部授权。
 */
public record CreateRoleRequest(
        @NotBlank String roleCode,
        @NotBlank String roleName,
        String roleType,
        String status,
        Integer sortNo,
        Set<Long> menuIds,
        Set<Long> apiIds) {
}
