package com.acme.scaffold.security.token.service;

import com.acme.scaffold.security.token.rules.TokenVersionRules;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 版本进程内缓存。
 *
 * <p><b>存在意义</b>：版本校验位于每个已认证请求的关键路径上，逐请求回查 {@code sys_user}
 * 会把一次主键查询加到所有接口上。缓存 TTL 很短（默认 30s），
 * 即「权限变更最多 30 秒后全局生效」——这比「立刻生效但每个请求多一次 DB 往返」更划算，
 * 也比引入 Redis 作为强依赖更轻（本框架允许无 Redis 的 local 部署）。
 *
 * <p><b>为什么不缓存「判定结果」而缓存「版本号」</b>：版本号是事实，不同 token 携带的版本不同，
 * 判定必须逐个请求做；缓存事实、不缓存结论，才不会把「某个旧 token 有效」误判成全局有效。
 *
 * <p><b>命中统计</b>：hit/miss/evict 三个 {@link AtomicLong} 计数器支撑「缓存监控」页面
 * （V29）展示命中率与淘汰规模。计数器只增不减、无锁，置于关键路径上的代价可忽略；
 * {@link #stats()} 一次性快照返回，避免把内部结构暴露给调用方。
 */
public class TokenVersionCache {

    private final long ttlMillis;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictCount = new AtomicLong();

    public TokenVersionCache(long ttlMillis) {
        this.ttlMillis = Math.max(ttlMillis, 0L);
    }

    /** 命中且未过期返回版本号，否则返回 null（并顺手清理过期条目）。 */
    public Integer get(Long userId) {
        if (userId == null) {
            return null;
        }
        Entry entry = entries.get(userId);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }
        if (TokenVersionRules.isExpired(System.currentTimeMillis(), entry.loadedAtMillis(), ttlMillis)) {
            entries.remove(userId, entry);
            evictCount.incrementAndGet();
            missCount.incrementAndGet();
            return null;
        }
        hitCount.incrementAndGet();
        return entry.version();
    }

    public void put(Long userId, Integer version) {
        if (userId == null || version == null) {
            return;
        }
        entries.put(userId, new Entry(version, System.currentTimeMillis()));
    }

    /** 版本递增后主动失效，保证下一次读取拿到新值。 */
    public void invalidate(Collection<Long> userIds) {
        if (userIds == null) {
            return;
        }
        userIds.forEach(entries::remove);
    }

    public int size() {
        return entries.size();
    }

    /** 清空全部条目（缓存监控页「清理」操作，清理后下次读取回源 DB）。 */
    public void clear() {
        evictCount.addAndGet(entries.size());
        entries.clear();
    }

    /**
     * 命中统计快照（V29 缓存监控页数据源）。
     *
     * @return 三元组：命中数、未命中数、过期/清理淘汰数；命中率 = hit / (hit + miss)
     */
    public long[] stats() {
        return new long[] {hitCount.get(), missCount.get(), evictCount.get()};
    }

    /** 配置的 TTL（毫秒），供监控页展示。 */
    public long ttlMillis() {
        return ttlMillis;
    }

    private record Entry(Integer version, long loadedAtMillis) {
    }
}
