package com.acme.scaffold.config.cache;

/**
 * 单个 Spring Cache 的进程内统计快照。
 *
 * <p>口径与「Token 版本缓存」一致：仅统计<b>本实例</b>视角的命中/未命中/淘汰，
 * 多实例部署时各实例计数独立（Redis 服务端无 per-cache 命中统计能力）。
 * 监控页按实例展示，聚合视图需由 Prometheus 等外部系统另行实现。
 *
 * @param name       缓存名称（清理操作按此定位）
 * @param type       实现类型：{@code REDIS} / {@code CONCURRENT_HASH_MAP}
 * @param entries    当前条目数；{@link StatisticsCache#UNAVAILABLE} 表示底层实现无法廉价获取（如 Redis 需 SCAN）
 * @param hitCount   累计命中数
 * @param missCount  累计未命中数
 * @param evictCount 累计淘汰数（单键驱逐 + 全量清理各记一次事件）
 */
public record CacheStats(
        String name,
        String type,
        long entries,
        long hitCount,
        long missCount,
        long evictCount) {
}
