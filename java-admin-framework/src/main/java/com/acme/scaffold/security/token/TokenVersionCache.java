package com.acme.scaffold.security.token;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

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
 */
public class TokenVersionCache {

    private final long ttlMillis;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();

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
            return null;
        }
        if (TokenVersionRules.isExpired(System.currentTimeMillis(), entry.loadedAtMillis(), ttlMillis)) {
            entries.remove(userId, entry);
            return null;
        }
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

    private record Entry(Integer version, long loadedAtMillis) {
    }
}
