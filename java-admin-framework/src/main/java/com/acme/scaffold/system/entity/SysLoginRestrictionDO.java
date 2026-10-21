package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 临时登录限制实体（V27，sys_login_restriction）。
 *
 * <p>状态机：{@link #STATUS_ACTIVE} → {@link #STATUS_RELEASED}。
 * <ul>
 *   <li>ACTIVE 且 {@code expireAt} 为空或晚于当前时间 → 限制生效，登录被拒绝；</li>
 *   <li>到期由定时任务自动解除（releaseType=AUTO_EXPIRED）；</li>
 *   <li>管理员手动解除（releaseType=MANUAL）。</li>
 * </ul>
 * 无 deleted 列：限制记录即审计留痕，不物理删除。
 */
@Data
public class SysLoginRestrictionDO {

    /** 生效中。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 已解除（手动或到期自动）。 */
    public static final String STATUS_RELEASED = "RELEASED";

    /** 解除方式：人工解除。 */
    public static final String RELEASE_MANUAL = "MANUAL";
    /** 解除方式：到期自动解除。 */
    public static final String RELEASE_AUTO_EXPIRED = "AUTO_EXPIRED";

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private String username;
    private String displayName;
    /** 限制原因（必填，展示给管理员并写入登录失败日志口径）。 */
    private String reason;
    /** 限制时长（分钟）；0 表示长期限制，仅可手动解除。 */
    private Integer durationMinutes = 0;
    private Long restrictedBy;
    private String restrictedByName;
    private LocalDateTime restrictedAt;
    /** 到期时间；durationMinutes=0 时为 null（长期）。 */
    private LocalDateTime expireAt;
    private LocalDateTime releasedAt;
    private Long releasedBy;
    private String releaseType;
    private String status = STATUS_ACTIVE;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
