package com.acme.scaffold.monitor;

/**
 * 单项缓存统计（V29 缓存监控页数据源）。
 *
 * <p>当前进程内缓存仅「Token 版本缓存」一类（ConcurrentHashMap + 30s TTL，见
 * {@code TokenVersionService}）。record 设计为可扩展形态：后续新增缓存实现时
 * 向列表追加一项即可，前端表格无需改动。
 *
 * @param name       缓存名称（唯一标识，清理操作按此定位）
 * @param type       实现类型（如 CONCURRENT_HASH_MAP，便于后续接 Caffeine/Redis 时区分）
 * @param entries    当前条目数
 * @param hitCount   累计命中数
 * @param missCount  累计未命中数（含过期未命中）
 * @param evictCount 累计淘汰数（过期清理 + 手动清理 + 主动失效）
 * @param hitRate    命中率（hit / (hit + miss)），无访问时为 null
 * @param ttlMillis  配置的 TTL（毫秒）；null 表示不过期
 * @param description 用途说明
 */
public record CacheStatView(
        String name,
        String type,
        long entries,
        long hitCount,
        long missCount,
        long evictCount,
        Double hitRate,
        Long ttlMillis,
        String description) {

    /**
     * 计算命中率：无任何访问（hit+miss=0）时返回 null，由前端展示「--」，
     * 避免 0/0 产生 NaN 或误导性的 0%。
     */
    public static Double hitRateOf(long hit, long miss) {
        long total = hit + miss;
        if (total <= 0) {
            return null;
        }
        return (double) hit / total;
    }
}
