package com.acme.scaffold.realtime.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 连接注册表（对应设计稿 §3.4 路由表的本节点实现）。
 *
 * <p>两个索引：
 * <ul>
 *   <li>{@code connId → RouteEntry}：连接维度操作（心跳刷新、断开清理）；</li>
 *   <li>{@code userId → connId}：定向推送维度（deliver 按用户寻路，§3.4）。</li>
 * </ul>
 * 约束：本节点内一个用户同时只保一条活跃连接（重连时驱逐旧连接，
 * 与设计稿「epoch 代际」语义一致）。
 *
 * <p>线程安全：网关事件循环单线程驱动；测试可并发调用，内部以 synchronized 保护不变量。
 */
public final class ConnectionRegistry {

    private final Map<String, RouteEntry> byConn = new HashMap<>();
    private final Map<String, String> connByUser = new HashMap<>();

    /**
     * 注册连接；同用户旧连接被驱逐并返回其 connId（调用方负责关闭旧通道）。
     *
     * @return 被驱逐的旧连接 ID；无旧连接返回 null
     */
    public synchronized String register(String connId, String userId, String gatewayId, long now) {
        RouteEntry entry = new RouteEntry(connId, userId, gatewayId, nextEpoch(userId, now), now);
        String previous = connByUser.put(userId, connId);
        if (previous != null && !previous.equals(connId)) {
            byConn.remove(previous);
        }
        byConn.put(connId, entry);
        return previous;
    }

    private long nextEpoch(String userId, long now) {
        RouteEntry current = connByUser.containsKey(userId) ? byConn.get(connByUser.get(userId)) : null;
        // 代际递增：重连用户的 epoch 单调增，旧连接迟到帧可据此识别
        return (current == null ? 0 : current.epoch()) + 1;
    }

    /** 心跳/活动刷新。 */
    public synchronized void touch(String connId, long now) {
        RouteEntry current = byConn.get(connId);
        if (current != null) {
            byConn.put(connId, current.touch(now));
        }
    }

    /** 连接关闭清理（异常断开 channelInactive 回调即走此路径，§3.4 删除方）。 */
    public synchronized void remove(String connId) {
        RouteEntry removed = byConn.remove(connId);
        // 仅当该 userId 仍指向此连接时才解除索引（防止误删新连接的索引）
        if (removed != null && connId.equals(connByUser.get(removed.userId()))) {
            connByUser.remove(removed.userId());
        }
    }

    /** 按用户清理（踢人下线场景）。 */
    public synchronized void removeByUser(String userId) {
        String connId = connByUser.remove(userId);
        if (connId != null) {
            byConn.remove(connId);
        }
    }

    /** 全清（优雅退出第 7 步：删除路由表条目，§3.6）。 */
    public synchronized void clear() {
        byConn.clear();
        connByUser.clear();
    }

    /** 定向推送寻路：用户 → 本节点连接。 */
    public synchronized Optional<RouteEntry> findConn(String userId) {
        String connId = connByUser.get(userId);
        if (connId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byConn.get(connId));
    }

    public synchronized Optional<RouteEntry> entry(String connId) {
        return Optional.ofNullable(byConn.get(connId));
    }

    public synchronized int size() {
        return byConn.size();
    }

    public synchronized int userCount() {
        return connByUser.size();
    }

    /**
     * TTL 过期清理（§3.4：优雅退出与异常断开之外的兜底，防幽灵连接）。
     *
     * @return 被清理的连接 ID 列表
     */
    public synchronized List<String> expireIdle(long ttlMs, long now) {
        List<String> expired = new ArrayList<>();
        for (RouteEntry entry : byConn.values()) {
            if (entry.isExpired(ttlMs, now)) {
                expired.add(entry.connId());
            }
        }
        for (String connId : expired) {
            remove(connId);
        }
        return expired;
    }

    /** 全量快照（运维查询/调试）。 */
    public synchronized List<RouteEntry> snapshot() {
        return List.copyOf(byConn.values());
    }
}
