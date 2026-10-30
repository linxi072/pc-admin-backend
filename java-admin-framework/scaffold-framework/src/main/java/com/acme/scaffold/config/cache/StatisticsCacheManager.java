package com.acme.scaffold.config.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存管理器装饰器：委托真实 {@link CacheManager}（生产为 Redis 实现），
 * 对其暴露的每个 {@link Cache} 套一层 {@link StatisticsCache} 记账。
 *
 * <p>装饰按缓存名缓存包装实例（{@code computeIfAbsent}），保证同一缓存在进程内
 * 只有一份计数器——Spring 的缓存拦截器每次操作都会 {@code getCache(name)}，
 * 若每次新建包装器则计数永远归零。
 *
 * <p>仅「本进程实际使用过的缓存」会出现在 {@link #stats()} 与可清理集合中：
 * 未使用过的缓存不占进程内存，其统计与清理对本实例也无意义（Redis 侧数据
 * 由 TTL 自然过期）。清理语义因此收敛为「清理本实例已知缓存」，未知名称返回
 * {@code false}，交由调用方输出 404 语义。
 */
public class StatisticsCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final Map<String, StatisticsCache> wrapped = new ConcurrentHashMap<>();

    public StatisticsCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return null;
        }
        return wrapped.computeIfAbsent(name, n -> new StatisticsCache(cache, StatisticsCache.typeOf(cache)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    /** 进程内统计快照（按缓存名字典序，保证监控页展示稳定）。 */
    public List<CacheStats> stats() {
        return wrapped.values().stream()
                .map(StatisticsCache::snapshot)
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .toList();
    }

    /**
     * 清理指定名称的缓存。
     *
     * @return true=已清理；false=本实例未使用过该缓存（监控页列表外的手工名称）
     */
    public boolean clearCache(String name) {
        StatisticsCache cache = wrapped.get(name);
        if (cache == null) {
            return false;
        }
        cache.clear();
        return true;
    }
}
