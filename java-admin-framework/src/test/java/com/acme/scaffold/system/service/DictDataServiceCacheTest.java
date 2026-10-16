package com.acme.scaffold.system.service;

import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateDictDataRequest;
import com.acme.scaffold.system.dto.DictDataVO;
import com.acme.scaffold.system.dto.UpdateDictDataRequest;
import com.acme.scaffold.system.entity.SysDictDataDO;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link DictDataService} 缓存行为测试（@Cacheable / @CacheEvict 试点）。
 *
 * <p>用轻量 Spring 上下文（{@code @EnableCaching} + ConcurrentMapCacheManager）驱动真实缓存代理，
 * 以 {@code mockStatic} 截断 jOOQ 静态工具作为打库替身；缓存行为不依赖打库次数核验
 * （项目约定：DB 交互路径由 MySQL 集成测试覆盖，mock 链式核验脆弱），而是通过
 * {@link CacheManager} 的可观测状态断言：
 * <ul>
 *   <li>命中：缓存中存在对应键，且二次读取返回首次的值（打库替身按次返回不同值）；</li>
 *   <li>驱逐：写操作后对应键消失，后续读取拿到新值（证明回源）；</li>
 *   <li>归一：空/空白类型码共用同一键。</li>
 * </ul>
 * 不依赖数据库与 Redis，离线可跑。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DictDataServiceCacheTest.TestConfig.class)
class DictDataServiceCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        DictDataService dictDataService() {
            return new DictDataService(mock(DSLContext.class));
        }
    }

    @Autowired
    private DictDataService service;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 用例间缓存隔离：同一 Spring 上下文内所有用例共享同一个 CacheManager，
     * 不清空会出现「上一个用例的缓存残留命中」，导致打库计数错位。
     */
    @BeforeEach
    void clearCacheBetweenTests() {
        Cache cache = cacheManager.getCache(DictDataService.CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    private static SysDictDataDO row(Long id, String type, String label, String value) {
        SysDictDataDO d = new SysDictDataDO();
        d.setId(id);
        d.setDictTypeCode(type);
        d.setDictLabel(label);
        d.setDictValue(value);
        d.setDictSort(1);
        d.setStatus("ACTIVE");
        return d;
    }

    /**
     * 打库替身：每次调用返回 label 不同的数据（第 N 次返回 label="第N次"）。
     * 若读取命中缓存则永远拿到「第1次」，从而在不核验调用次数的前提下证明缓存生效。
     */
    private void stubFetchWithChangingResult(MockedStatic<JooqWriters> writers) {
        AtomicInteger calls = new AtomicInteger();
        writers.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysDictDataDO.class), any(),
                        any(OrderField[].class)))
                .thenAnswer(inv -> {
                    int n = calls.incrementAndGet();
                    return List.of(row((long) n, "gender", "第" + n + "次", "M"));
                });
    }

    /** 固定返回一条数据的打库替身（写路径测试用，读结果稳定）。 */
    private void stubFetchFixed(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysDictDataDO.class), any(),
                        any(OrderField[].class)))
                .thenReturn(List.of(row(1L, "gender", "男", "M")));
    }

    private void stubWrites(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.count(any(), any(), any())).thenReturn(0L);
        writers.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.fetchById(any(), any(), eq(SysDictDataDO.class), any()))
                .thenReturn(row(1L, "gender", "男", "M"));
        writers.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.delete(any(), any(), any(), eq(true))).thenAnswer(inv -> null);
    }

    private Cache dictCache() {
        Cache cache = cacheManager.getCache(DictDataService.CACHE_NAME);
        assertNotNull(cache);
        return cache;
    }

    private String cachedLabel(String key) {
        Cache.ValueWrapper wrapper = dictCache().get(key);
        if (wrapper == null) {
            return null;
        }
        return ((List<?>) wrapper.get()).isEmpty() ? null
                : ((DictDataVO) ((List<?>) wrapper.get()).get(0)).dictLabel();
    }

    @Test
    void secondReadServedFromCache() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubFetchWithChangingResult(writers);

            assertEquals("第1次", service.listByType("gender").get(0).dictLabel());
            // 命中缓存：打库替身按次返回不同值，二次读取仍是首次结果
            assertEquals("第1次", service.listByType("gender").get(0).dictLabel());
            assertEquals("第1次", cachedLabel("gender"));
        }
    }

    @Test
    void blankTypeCodeNormalizedToSameCacheKey() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubFetchWithChangingResult(writers);

            assertEquals("第1次", service.listByType(null).get(0).dictLabel());
            // null / 空串 / 空白串归一为同一键，均命中首次缓存
            assertEquals("第1次", service.listByType("").get(0).dictLabel());
            assertEquals("第1次", service.listByType("   ").get(0).dictLabel());
            assertEquals("第1次", cachedLabel(DictDataService.ALL_TYPES));
        }
    }

    @Test
    void createEvictsMatchingKeyOnly() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubFetchFixed(writers);
            stubWrites(writers);

            service.listByType("gender"); // 填充 gender 键
            service.create(new CreateDictDataRequest("status", "启用", "1", 1, "ACTIVE", null));

            assertEquals("男", cachedLabel("gender")); // 其它键不受影响
            assertNull(cachedLabel("status"));         // 被精确驱逐（从未填充则为空）
        }
    }

    @Test
    void updateEvictsAllEntriesAndRereadHitsDb() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubFetchWithChangingResult(writers);
            stubWrites(writers);

            service.listByType("gender"); // 填充（第1次）
            service.update(1L, new UpdateDictDataRequest("gender", "男", "M", 1, "ACTIVE", null));
            assertNull(cachedLabel("gender")); // 全量驱逐

            // 驱逐后回源：拿到打库替身的第二次结果
            assertEquals("第2次", service.listByType("gender").get(0).dictLabel());
        }
    }

    @Test
    void deleteEvictsAllEntriesAndRereadHitsDb() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubFetchWithChangingResult(writers);
            stubWrites(writers);

            service.listByType("gender"); // 填充（第1次）
            service.delete(1L);
            assertNull(cachedLabel("gender")); // 全量驱逐

            assertEquals("第2次", service.listByType("gender").get(0).dictLabel());
        }
    }

    @Test
    void createEvictsGenderKeyWhenWritingSameType() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubFetchWithChangingResult(writers);
            stubWrites(writers);

            service.listByType("gender"); // 填充（第1次）
            service.create(new CreateDictDataRequest("gender", "女", "F", 2, "ACTIVE", null));
            assertNull(cachedLabel("gender")); // 同类型写入 → 精确驱逐该键

            assertEquals("第2次", service.listByType("gender").get(0).dictLabel());
        }
    }
}
