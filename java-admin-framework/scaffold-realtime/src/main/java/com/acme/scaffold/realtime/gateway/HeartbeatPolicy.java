package com.acme.scaffold.realtime.gateway;

/**
 * 心跳与保活策略。
 *
 * @param pingIntervalMs  服务端 ping 周期（30s，兼顾 NAT/LB 60s+ 超时）
 * @param idleTimeoutMs   读写空闲断开（90s）
 * @param routeTtlMs      路由表 TTL（90s，留 3 个心跳周期余量）
 */
public record HeartbeatPolicy(long pingIntervalMs, long idleTimeoutMs, long routeTtlMs) {

    /** 设计稿默认值。 */
    public static HeartbeatPolicy designDefault() {
        return new HeartbeatPolicy(30_000L, 90_000L, 90_000L);
    }

    public HeartbeatPolicy {
        if (pingIntervalMs <= 0) {
            throw new IllegalArgumentException("ping 周期必须为正: " + pingIntervalMs);
        }
        if (idleTimeoutMs <= pingIntervalMs) {
            throw new IllegalArgumentException("空闲超时必须大于 ping 周期: " + idleTimeoutMs + " <= " + pingIntervalMs);
        }
        if (routeTtlMs < pingIntervalMs * 3) {
            throw new IllegalArgumentException("路由 TTL 至少为 3 个 ping 周期（余量）: " + routeTtlMs);
        }
    }

    /** 是否到达发送 ping 的时机。 */
    public boolean isPingDue(long lastPingAt, long now) {
        return now - lastPingAt >= pingIntervalMs;
    }

    /** 读写空闲是否超时（应断开）。 */
    public boolean isIdleExpired(long lastReadAt, long now) {
        return now - lastReadAt >= idleTimeoutMs;
    }

    /** 路由表条目是否过期。 */
    public boolean isRouteExpired(long lastSeen, long now) {
        return now - lastSeen > routeTtlMs;
    }
}
