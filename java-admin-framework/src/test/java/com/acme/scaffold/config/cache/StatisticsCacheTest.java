package com.acme.scaffold.config.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StatisticsCache} 单元测试：命中/未命中/淘汰记账、类型推断、条目数估算、委托语义。
 * 不依赖 Redis 与 Spring 容器，纯逻辑离线验证。
 */
class StatisticsCacheTest {

    private ConcurrentMapCache delegate;
    private StatisticsCache cache;

    @BeforeEach
    void setUp() {
        delegate = new ConcurrentMapCache("dict-data");
        cache = new StatisticsCache(delegate, StatisticsCache.TYPE_LOCAL);
    }

    @Test
    void missThenHitAccounting() {
        assertNull(cache.get("k1"));
        assertEquals(0, cache.snapshot().hitCount());
        assertEquals(1, cache.snapshot().missCount());

        cache.put("k1", "v1");
        Cache.ValueWrapper wrapper = cache.get("k1");
        assertTrue(wrapper != null && "v1".equals(wrapper.get()));
        assertEquals(1, cache.snapshot().hitCount());
        assertEquals(1, cache.snapshot().missCount());
    }

    @Test
    void typedGetAndLoaderCountAsReads() {
        assertNull(cache.get("k", String.class));
        assertEquals(1, cache.snapshot().missCount());

        cache.put("k", "v");
        assertEquals("v", cache.get("k", String.class));
        assertEquals(1, cache.snapshot().hitCount());

        // Callable 路径：未命中时加载并计数为 miss 后由委托回填
        Object loaded = cache.get("k2", (Callable<Object>) () -> "v2");
        assertEquals("v2", loaded);
        assertEquals(1, cache.snapshot().hitCount());
        assertEquals(2, cache.snapshot().missCount());
    }

    @Test
    void evictAndClearIncrementEvictCount() {
        cache.put("a", 1);
        cache.put("b", 2);
        cache.evict("a");
        assertEquals(1, cache.snapshot().evictCount());
        assertNull(cache.get("a"));

        cache.clear();
        assertEquals(2, cache.snapshot().evictCount());
        assertNull(cache.get("b"));
        // clear 后底层确认为空
        assertEquals(0, cache.estimateSize());
    }

    @Test
    void putIfAbsentDelegatesWithoutCounting() {
        assertNull(cache.putIfAbsent("k", "v1"));
        assertEquals(0, cache.snapshot().hitCount() + cache.snapshot().missCount());
        Cache.ValueWrapper existing = cache.putIfAbsent("k", "v2");
        assertTrue(existing != null && "v1".equals(existing.get()));
        assertEquals(0, cache.snapshot().evictCount());
    }

    @Test
    void estimateSizeForLocalMapAndUnknownImpl() {
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(2, cache.estimateSize());

        // 非 Map 原生缓存（模拟 RedisCache）→ 不可用哨兵值
        StatisticsCache redisLike = new StatisticsCache(new RedisLikeCache(), StatisticsCache.TYPE_REDIS);
        assertEquals(StatisticsCache.UNAVAILABLE, redisLike.estimateSize());
    }

    @Test
    void typeOfInfersLocalAndRedis() {
        assertEquals(StatisticsCache.TYPE_LOCAL, StatisticsCache.typeOf(delegate));
        assertEquals(StatisticsCache.TYPE_REDIS, StatisticsCache.typeOf(new RedisLikeCache()));
        // 其它实现回落为大写类名
        assertEquals("WEIRDLIKECACHE", StatisticsCache.typeOf(new WeirdLikeCache()));
    }

    @Test
    void nameAndNativeCacheDelegate() {
        assertEquals("dict-data", cache.getName());
        assertTrue(cache.getNativeCache() instanceof Map);
    }

    /** 类名含 Redis 且原生缓存非 Map 的 Cache 桩（模拟 RedisCache 形态）。 */
    private static final class RedisLikeCache extends NoNativeMapCache {
    }

    /** 既非 Map 原生、类名也不含 Redis 的 Cache 桩（回落分支）。 */
    private static final class WeirdLikeCache extends NoNativeMapCache {
    }

    /** 原生缓存为非 Map 对象的最小 Cache 实现。 */
    private abstract static class NoNativeMapCache implements Cache {

        private final Object nativeCache = new Object();

        @Override
        public String getName() {
            return "stub";
        }

        @Override
        public Object getNativeCache() {
            return nativeCache;
        }

        @Override
        public ValueWrapper get(Object key) {
            return null;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return null;
        }

        @Override
        public void put(Object key, Object value) {
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return null;
        }

        @Override
        public void evict(Object key) {
        }

        @Override
        public void clear() {
        }
    }
}
