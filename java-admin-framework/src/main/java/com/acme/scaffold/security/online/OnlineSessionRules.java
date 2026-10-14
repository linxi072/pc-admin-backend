package com.acme.scaffold.security.online;

import com.acme.scaffold.system.entity.SysOnlineSessionDO;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 在线会话纯规则（无 Spring / 无 DB 依赖，便于单测）。
 *
 * <p>集中两类判定：
 * <ul>
 *   <li><b>会话展示状态</b>：列表列「会话状态」的取值来源，优先级
 *       {@code RESTRICTED > IDLE > ONLINE}——被限制登录的用户即使会话仍存活也标记为已限制；</li>
 *   <li><b>心跳节流</b>：已认证请求经 {@link OnlineSessionFilter} 更新 {@code last_active_at}，
 *       按「每用户每设备每 {@link #DEFAULT_HEARTBEAT_INTERVAL} 最多一次写库」节流，
 *       避免高 QPS 下心跳写放大。</li>
 * </ul>
 */
public final class OnlineSessionRules {

    /** 默认心跳间隔：超过该时长未活跃的请求才触发一次 last_active_at 写库。 */
    public static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(60);

    private OnlineSessionRules() {
    }

    /**
     * 计算会话展示状态。
     *
     * @param lastActiveAt  最近活跃时间（心跳写入值，不可为空）
     * @param restricted    该用户当前是否存在生效的登录限制
     * @param idleThreshold 空闲判定阈值：最近活跃距当前超过该值视为空闲
     * @param now           当前时间（调用方注入，保证可测）
     * @return {@link SysOnlineSessionDO#STATUS_ONLINE} / {@code IDLE} / {@code RESTRICTED}
     */
    public static String displayStatus(LocalDateTime lastActiveAt, boolean restricted,
                                       Duration idleThreshold, LocalDateTime now) {
        if (restricted) {
            return "RESTRICTED";
        }
        if (lastActiveAt == null) {
            return SysOnlineSessionDO.STATUS_ONLINE;
        }
        Duration idle = Duration.between(lastActiveAt, now);
        if (idle.compareTo(idleThreshold) > 0) {
            return "IDLE";
        }
        return SysOnlineSessionDO.STATUS_ONLINE;
    }

    /** 心跳是否到点：距上次心跳不足一个间隔则跳过（节流写库）。 */
    public static boolean heartbeatDue(long nowMillis, long lastBeatMillis, long intervalMillis) {
        return nowMillis - lastBeatMillis >= intervalMillis;
    }
}
