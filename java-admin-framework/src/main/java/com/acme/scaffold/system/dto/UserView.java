package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 用户视图，绝不外泄 passwordHash / tokenVersion 等敏感字段。
 */
public record UserView(Long id, String username, String displayName, String mobile, String email,
                      Long primaryOrgId, String status, Set<String> roleCodes, LocalDateTime createdAt) {

    public static UserView from(com.acme.scaffold.system.entity.SysUserDO u, Set<String> roleCodes) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getMobile(), u.getEmail(),
                u.getPrimaryOrgId(), u.getStatus(), roleCodes, u.getCreatedAt());
    }
}
