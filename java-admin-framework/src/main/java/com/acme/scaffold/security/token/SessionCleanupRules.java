package com.acme.scaffold.security.token;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 会话空闲回收纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p>用于「用户异常退出（进程崩溃 / 连接中断）后刷新令牌及时失效」：
 * 以刷新令牌的最近活动时间为基准（{@code last_used_at} 缺失则回退 {@code issued_at}），
 * 若最近活动时间早于 {@code now - idleTimeout}，即视为空闲可回收。
 */
public final class SessionCleanupRules {

    private SessionCleanupRules() {
    }

    /**
     * 会话是否因空闲超时需要被回收。
     *
     * @param lastUsedAt   最近使用时间（刷新令牌时更新）；可为 null
     * @param issuedAt     签发时间（登录时写入）；可为 null
     * @param now          当前时间
     * @param idleTimeout  空闲阈值；&lt;=0 视为不启用回收
     * @return true 表示应回收
     */
    public static boolean isIdle(LocalDateTime lastUsedAt, LocalDateTime issuedAt, LocalDateTime now, Duration idleTimeout) {
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            return false;
        }
        LocalDateTime activity = lastUsedAt != null ? lastUsedAt : issuedAt;
        if (activity == null) {
            // 无活动基准，保守不回收（避免误杀刚签发但未刷新过的正常会话）
            return false;
        }
        return activity.plus(idleTimeout).isBefore(now);
    }
}
