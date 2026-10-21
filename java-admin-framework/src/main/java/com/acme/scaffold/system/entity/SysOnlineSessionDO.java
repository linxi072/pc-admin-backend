package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线会话跟踪实体（V27，sys_online_session）。
 *
 * <p>业务键为 {@code (tenant_id, user_id, device_id)}：同一用户同一设备仅一行，
 * 重复登录走 upsert（重置登录时间与会话状态）。生命周期：
 * <ul>
 *   <li>登录成功 → 插入/重置为 {@link #STATUS_ONLINE}；</li>
 *   <li>已认证请求 → 过滤器节流更新 {@code lastActiveAt}（心跳）；</li>
 *   <li>登出 / 强制下线 / 限制登录连带下线 → {@link #STATUS_OFFLINE} + offlineReason；</li>
 *   <li>刷新令牌过期 / 空闲回收 → 由 {@code SessionCleanupService} 联动置离线。</li>
 * </ul>
 * 无 deleted 列：历史行作为「最近下线记录」留存，查询侧按 status 过滤。
 */
@Data
public class SysOnlineSessionDO {

    /** 在线。 */
    public static final String STATUS_ONLINE = "ONLINE";
    /** 已下线（LOGOUT / FORCE_LOGOUT / RESTRICTED / EXPIRED / INACTIVITY）。 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private String deviceId;
    private String clientId = "web";
    private String username;
    private String displayName;
    private String loginIp;
    private String userAgent;

    /** 本次登录时间（登录/重新注册时刷新）。 */
    private LocalDateTime loginAt;
    /** 最近活跃时间（心跳节流更新，登录时初始化）。 */
    private LocalDateTime lastActiveAt;

    private String status = STATUS_ONLINE;
    /** 下线原因：LOGOUT / FORCE_LOGOUT / RESTRICTED / EXPIRED / INACTIVITY。 */
    private String offlineReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
