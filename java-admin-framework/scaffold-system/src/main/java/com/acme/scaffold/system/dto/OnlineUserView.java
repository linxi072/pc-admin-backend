package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 在线用户会话视图（一行 = 一个在线设备会话）。
 *
 * <p>status 为展示态（{@code ONLINE} / {@code IDLE} / {@code RESTRICTED}），
 * 由 {@code OnlineSessionRules.displayStatus} 计算，与底层行状态
 * （sys_online_session.status 仅 ONLINE/OFFLINE）区分。
 */
public record OnlineUserView(
        Long id,
        Long userId,
        String username,
        String displayName,
        String loginIp,
        LocalDateTime loginAt,
        LocalDateTime lastActiveAt,
        String status,
        String deviceId,
        String userAgent) {
}
