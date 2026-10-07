package com.acme.scaffold.monitor;

import java.time.LocalDateTime;

/**
 * 在线会话视图：基于未过期且未吊销的刷新令牌（sys_refresh_token）推导。
 * 每次登录签发一个 refresh token，因此「活跃令牌数≈活跃会话数」，按 session_id 去重。
 */
public record OnlineSessionView(
        Long userId,
        String username,
        String displayName,
        String sessionId,
        String clientId,
        String ipAddress,
        String userAgent,
        LocalDateTime issuedAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        /** 距过期剩余分钟数 */
        long remainingMinutes) {
}
