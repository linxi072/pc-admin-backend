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

    private Long primaryOrgId;

    /** 单一角色ID：用户仅可绑定一个角色（V6 收敛后由 sys_user.role_id 直接承载）。 */
    private Long roleId;

    /** 单一部门ID：用户仅可归属一个部门。 */
    private Long orgId;

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
