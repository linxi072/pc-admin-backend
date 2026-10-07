package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 个人中心视图：只读展示「我的」资料，绝不外泄 passwordHash / tokenVersion。
 * <p>绑定状态由 mobile / email 是否为空推导（mobileBound / emailBound），
 * 前端无需再判断字段值，可直接渲染「已绑定 / 去绑定」。
 *
 * @param mobileBound     是否已绑定手机
 * @param emailBound      是否已绑定邮箱
 * @param roleName        单一角色名称（V6 收敛后只有一个）
 * @param orgName         单一部门名称
 */
public record ProfileView(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        String mobile,
        boolean mobileBound,
        String email,
        boolean emailBound,
        String roleName,
        String orgName,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        LocalDateTime createdAt) {
}