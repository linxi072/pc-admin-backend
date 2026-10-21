package com.acme.scaffold.config.cache;

import org.springframework.cache.Cache;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存统计装饰器：委托底层 {@link Cache}，以进程内计数器记录命中 / 未命中 / 淘汰。
 *
 * <p>Spring Cache 抽象本身不提供统计能力（{@code RedisCache} 无 per-cache 命中计数，
 * 服务端 {@code keyspace_hits} 又无法按缓存名拆分），故以装饰器在应用侧记账，
 * 与 {@code TokenVersionService} 的进程内统计保持同一口径。计数器用 {@link LongAdder}
 * 保证高并发下的累加效率；装饰器不改变任何读写语义，仅旁路观测。
 *
 * <p>计数规则（与监控页字段一一对应）：
 * <ul>
 *   <li>{@code get(key)} / {@code get(key, type)}：返回非 null 记命中，返回 null 记未命中；</li>
 *   <li>{@code get(key, loader)}：包装 loader 区分「命中」与「未命中后加载」，后者记未命中
 *       （加载是回源行为，计入命中会稀释命中率语义）；</li>
 *   <li>{@code evict(key)} 与 {@code clear()} 各记一次淘汰事件（不展开具体条目数）；</li>
 *   <li>{@code put} / {@code putIfAbsent} 不计入（写入非读取，不计命中率分母）。</li>
 * </ul>
 */
public class StatisticsCache implements Cache {

    /** 底层实现无法廉价给出条目数时使用（Redis 需 SCAN，不做 O(N) 扫描）。 */
    public static final long UNAVAILABLE = -1L;

    static final String TYPE_REDIS = "REDIS";
    static final String TYPE_LOCAL = "CONCURRENT_HASH_MAP";

    private final Cache delegate;
    private final String type;
    private final LongAdder hitCount = new LongAdder();
    private final LongAdder missCount = new LongAdder();
    private final LongAdder evictCount = new LongAdder();

    public StatisticsCache(Cache delegate, String type) {
        this.delegate = delegate;
        this.type = type;
    }

    /** 按底层实现推断展示类型：本地映射 / Redis / 其它（回落类名）。 */
    public static String typeOf(Cache cache) {
        if (cache.getNativeCache() instanceof Map) {
            return TYPE_LOCAL;
        }
        String simple = cache.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        return simple.contains("REDIS") ? TYPE_REDIS : simple;
    }

    /** 当前条目数；底层不支持廉价获取时返回 {@link #UNAVAILABLE}。 */
    public long estimateSize() {
        if (delegate.getNativeCache() instanceof Map<?, ?> map) {
            return map.size();
        }
        return UNAVAILABLE;
    }

    public CacheStats snapshot() {
        return new CacheStats(getName(), type, estimateSize(),
                hitCount.sum(), missCount.sum(), evictCount.sum());
    }

    private void countRead(boolean hit) {
        if (hit) {
            hitCount.increment();
        } else {
            missCount.increment();
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper wrapper = delegate.get(key);
        countRead(wrapper != null);
        return wrapper;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T value = delegate.get(key, type);
        countRead(value != null);
        return value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        // 包装 loader 以区分「命中」与「未命中后加载」：loader 未被触发即命中，
        // 触发即未命中（加载不算命中，否则命中率被稀释语义污染）。
        boolean[] loaded = {false};
        Callable<T> tracking = () -> {
            loaded[0] = true;
            return valueLoader.call();
        };
        T value = delegate.get(key, tracking);
        countRead(!loaded[0]);
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
        evictCount.increment();
    }

    @Override
    public void clear() {
        delegate.clear();
        // 全量清理记一次淘汰事件：具体条目数对 Redis 不可知，对本地映射也以事件语义统一
        evictCount.increment();
    }
}
