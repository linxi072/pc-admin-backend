package com.acme.scaffold.realtime.gateway;

/**
 * 路由表条目（本节点连接 → 用户寻路镜像）。
 *
 * <p>epoch 用于区分同用户重连的代际：旧连接的迟到帧可被识别并丢弃。
 *
 * @param connId    连接 ID（本节点唯一）
 * @param userId    用户 ID（分区键同源）
 * @param gatewayId 网关节点 ID（跨节点单播寻址用）
 * @param epoch     连接代际（每次握手递增）
 * @param lastSeen  最后心跳/活动时间（TTL 判定）
 */
public record RouteEntry(String connId, String userId, String gatewayId, long epoch, long lastSeen) {

    public RouteEntry {
        if (connId == null || connId.isBlank()) {
            throw new IllegalArgumentException("connId 不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new IllegalArgumentException("gatewayId 不能为空");
        }
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch 不能为负: " + epoch);
        }
    }

    /** 心跳刷新：返回更新了 lastSeen 的副本。 */
    public RouteEntry touch(long now) {
        return new RouteEntry(connId, userId, gatewayId, epoch, now);
    }

    /** TTL 判定（留 3 个心跳周期余量）。 */
    public boolean isExpired(long ttlMs, long now) {
        return now - lastSeen > ttlMs;
    }
}
