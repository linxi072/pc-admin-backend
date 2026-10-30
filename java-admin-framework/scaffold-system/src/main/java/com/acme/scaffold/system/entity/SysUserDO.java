package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysUserDO {

    private Long id;

    private Long tenantId = 0L;
    private String username;
    private String passwordHash;
    private String displayName;
    private String mobile;
    private String email;

    /** 头像相对路径（V10 新增，如 /uploads/avatar/1_1710000000.png）。 */
    private String avatarUrl;

    // ---- 通知偏好（V10 新增）：三渠道独立开关，便于任意组合 ----
    private Integer notifySiteMessage = 1;
    private Integer notifyEmail = 1;
    private Integer notifyMobile = 0;

    // ---- 隐私偏好（V10 新增）----
    /** 是否在个人中心展示我的登录记录。 */
    private Integer showLoginLog = 1;
    /** 对外展示时是否脱敏手机号（如 138****8000）。 */
    private Integer maskMobile = 1;
    /** 是否允许被其他用户搜索到。 */
    private Integer discoverable = 1;

    // 角色 / 部门改为多对多（sys_user_role / sys_user_org 关联表承载），
    // 主角色 / 主部门由关联表 is_primary 标记推导，sys_user 不再保留单值列。

    private String status;
    private Integer failedLoginCount = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime passwordChangedAt;
    private Integer passwordExpired = 0;
    private Integer tokenVersion = 1;
    private Integer mfaEnabled = 0;
    private LocalDateTime lastLoginAt;

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
