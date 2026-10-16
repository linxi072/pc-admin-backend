package com.acme.scaffold.config.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StatisticsCacheManager} 单元测试：委托与包装稳定性、统计快照、清理语义。
 * 以 {@link ConcurrentMapCacheManager} 为被委托对象，不依赖 Redis。
 */
class StatisticsCacheManagerTest {

    private ConcurrentMapCacheManager delegate;
    private StatisticsCacheManager manager;

    @BeforeEach
    void setUp() {
        delegate = new ConcurrentMapCacheManager();
        manager = new StatisticsCacheManager(delegate);
    }

    @Test
    void getCacheNamesDelegates() {
        delegate.setCacheNames(List.of("a", "b"));
        assertEquals(Set.of("a", "b"), Set.copyOf(manager.getCacheNames()));
    }

    @Test
    void repeatedGetCacheReturnsSameWrapperSoCountersAccumulate() {
        Cache first = manager.getCache("dict-data");
        Cache second = manager.getCache("dict-data");
        assertNotNull(first);
        assertSame(first, second);

        first.get("missing"); // miss
        first.put("k", "v");
        second.get("k");      // hit（同一包装器才能看到）
        List<CacheStats> stats = manager.stats();
        assertEquals(1, stats.size());
        assertEquals("dict-data", stats.get(0).name());
        assertEquals(1, stats.get(0).hitCount());
        assertEquals(1, stats.get(0).missCount());
    }

    @Test
    void getCacheReturnsNullWhenDelegateHasNone() {
        // 名称一律不存在的委托：验证空值传播与空统计
        CacheManager strict = new CacheManager() {
            @Override
            public Cache getCache(String name) {
                return null;
            }

            @Override
            public Collection<String> getCacheNames() {
                return List.of();
            }
        };
        StatisticsCacheManager strictManager = new StatisticsCacheManager(strict);
        assertNull(strictManager.getCache("anything"));
        assertTrue(strictManager.stats().isEmpty());
        assertFalse(strictManager.clearCache("anything"));
    }

    @Test
    void statsSortedByName() {
        manager.getCache("b-cache").put("k", 1);
        manager.getCache("a-cache").put("k", 1);
        List<CacheStats> stats = manager.stats();
        assertEquals(List.of("a-cache", "b-cache"),
                stats.stream().map(CacheStats::name).toList());
    }

    @Test
    void clearCacheOnlyForKnownWrappedCaches() {
        StatisticsCache wrapped = (StatisticsCache) manager.getCache("dict-data");
        wrapped.put("k", "v");
        assertNotNull(wrapped.get("k"));

        assertTrue(manager.clearCache("dict-data"));
        assertNull(wrapped.get("k")); // 底层确已清理
        // 清理记一次淘汰事件
        assertEquals(1, manager.stats().get(0).evictCount());

        // 未包装过的名称：不清底层、返回 false（404 语义）
        delegate.setCacheNames(List.of("never-used"));
        assertFalse(manager.clearCache("never-used"));
    }

    @Test
    void wrappedCacheTypeInferredFromDelegate() {
        Cache wrapped = manager.getCache("dict-data");
        assertEquals(StatisticsCache.TYPE_LOCAL,
                ((StatisticsCache) wrapped).snapshot().type());
    }
}
