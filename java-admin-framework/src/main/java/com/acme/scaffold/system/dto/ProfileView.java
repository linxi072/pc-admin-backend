package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心视图：只读展示「我的」资料，绝不外泄 passwordHash / tokenVersion。
 * <p>绑定状态由 mobile / email 是否为空推导（mobileBound / emailBound），
 * 前端无需再判断字段值，可直接渲染「已绑定 / 去绑定」。
 *
 * @param mobileBound     是否已绑定手机
 * @param emailBound      是否已绑定邮箱
 * @param roleNames       角色名称列表（多对多，可多个）
 * @param orgNames        部门名称列表（多对多，可多个）
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
        List<String> roleNames,
        List<String> orgNames,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime passwordChangedAt,
        LocalDateTime createdAt) {
}