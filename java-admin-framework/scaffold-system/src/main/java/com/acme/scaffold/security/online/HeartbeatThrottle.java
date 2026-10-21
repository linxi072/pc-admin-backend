package com.acme.scaffold.security.online;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 心跳节流器：按「用户+设备」维度控制 last_active_at 写库频率。
 *
 * <p><b>为什么需要</b>：在线会话的「最近活跃时间」若每个已认证请求都写库，
 * 高 QPS 下会形成写放大（一个用户 1 秒 10 个请求 = 10 次 UPDATE）。
 * 节流为「每 key 每间隔最多一次」，把写量压到 {@code 在线设备数 / 间隔}。
 *
 * <p><b>内存边界</b>：key 为 {@code userId|deviceId}，条目数上限
 * {@link #MAX_ENTRIES}，超出时清理「超过 2 个周期未出现」的陈旧条目，
 * 保证长期运行下 Map 不会无限增长。清理失败不丢正确性（仅多写一次库）。
 *
 * <p>线程安全：{@link ConcurrentHashMap} + 原子 put，多请求线程并发调用安全。
 */
public final class HeartbeatThrottle {

    /** 条目数上限，超出触发一次陈旧清理。 */
    public static final int MAX_ENTRIES = 10_000;

    private final long intervalMillis;
    private final Map<String, Long> lastBeat = new ConcurrentHashMap<>();

    public HeartbeatThrottle(long intervalMillis) {
        this.intervalMillis = Math.max(intervalMillis, 1_000L);
    }

    /**
     * 尝试获取一次心跳配额：距上次不足一个间隔返回 false（跳过写库），
     * 否则记录本次时间并返回 true。
     */
    public boolean tryAcquire(String key, long nowMillis) {
        Long prev = lastBeat.get(key);
        if (prev != null && nowMillis - prev < intervalMillis) {
            return false;
        }
        lastBeat.put(key, nowMillis);
        if (lastBeat.size() > MAX_ENTRIES) {
            purge(nowMillis);
        }
        return true;
    }

    /** 清理超过 2 个周期未出现的陈旧条目，返回清理条数。 */
    public int purge(long nowMillis) {
        int before = lastBeat.size();
        lastBeat.entrySet().removeIf(e -> nowMillis - e.getValue() >= intervalMillis * 2);
        return before - lastBeat.size();
    }

    /** 当前跟踪的 key 数（测试与监控用）。 */
    public int size() {
        return lastBeat.size();
    }
}
