package com.acme.scaffold.monitor.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NPlusOneSuspectRegistry} 单元测试：有界留存、倒序检索、容量淘汰、累计口径、
 * 截断与空值安全、并发有界守恒。不依赖数据库或 Spring 容器，纯逻辑离线验证。
 */
@DisplayName("N+1 疑似事件留存测试")
class NPlusOneSuspectRegistryTest {

    private static NPlusOneSuspect suspect(String fingerprint) {
        return new NPlusOneSuspect(fingerprint, "select 1", 10, "main", 1_700_000_000_000L);
    }

    @Test
    @DisplayName("recent 返回最新在前的事件列表")
    void recentNewestFirst() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(10);
        registry.record("fp-a", "sql-a", 10);
        registry.record("fp-b", "sql-b", 11);
        registry.record("fp-c", "sql-c", 12);
        List<NPlusOneSuspect> recent = registry.recent(10);
        assertEquals(3, recent.size());
        assertEquals("fp-c", recent.get(0).fingerprint());
        assertEquals("fp-b", recent.get(1).fingerprint());
        assertEquals("fp-a", recent.get(2).fingerprint());
    }

    @Test
    @DisplayName("recent 对非法 limit 返回空列表，超过存量时返回全部")
    void recentLimitClamping() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(10);
        registry.record("fp-a", "sql-a", 10);
        assertTrue(registry.recent(0).isEmpty());
        assertTrue(registry.recent(-5).isEmpty());
        assertEquals(1, registry.recent(100).size());
    }

    @Test
    @DisplayName("容量淘汰：超出后按入队序淘汰最旧，存量恒等于容量")
    void boundedEviction() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(3);
        for (int i = 1; i <= 5; i++) {
            registry.record("fp-" + i, "sql-" + i, 10);
        }
        assertEquals(3, registry.size());
        List<NPlusOneSuspect> recent = registry.recent(10);
        assertEquals("fp-5", recent.get(0).fingerprint());
        assertEquals("fp-4", recent.get(1).fingerprint());
        assertEquals("fp-3", recent.get(2).fingerprint());
    }

    @Test
    @DisplayName("累计计数覆盖被淘汰事件（单调递增）")
    void totalCountsEvictedToo() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(2);
        for (int i = 1; i <= 5; i++) {
            registry.record("fp-" + i, "sql", 10);
        }
        assertEquals(5L, registry.total());
        assertEquals(0L, new NPlusOneSuspectRegistry().total());
    }

    @Test
    @DisplayName("最近登记时间随记录更新，清空后保持")
    void lastBreachAtLifecycle() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        assertEquals(0L, registry.lastBreachAt());
        registry.record("fp", "sql", 10);
        long at = registry.lastBreachAt();
        assertNotEquals(0L, at);
        registry.clear();
        assertEquals(at, registry.lastBreachAt());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("clear 清空留存但不清零累计计数")
    void clearKeepsTotals() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        registry.record("fp", "sql", 10);
        registry.clear();
        assertEquals(0, registry.size());
        assertEquals(1L, registry.total());
    }

    @Test
    @DisplayName("空指纹静默忽略，不占容量不计数")
    void nullFingerprintIgnored() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        registry.record(null, "sql", 10);
        registry.record("   ", "sql", 10);
        assertEquals(0, registry.size());
        assertEquals(0L, registry.total());
    }

    @Test
    @DisplayName("样本 SQL 超长截断至上限 + 截断标记")
    void sampleSqlTruncated() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        String longSql = "x".repeat(NPlusOneSuspectRegistry.MAX_SAMPLE_SQL_LENGTH + 100);
        registry.record("fp", longSql, 10);
        String sample = registry.recent(1).get(0).sampleSql();
        assertTrue(sample.startsWith("x".repeat(NPlusOneSuspectRegistry.MAX_SAMPLE_SQL_LENGTH)));
        assertTrue(sample.endsWith("...[truncated]"));
    }

    @Test
    @DisplayName("登记捕获当前线程名")
    void threadNameCaptured() {
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(5);
        registry.record("fp", "sql", 10);
        assertEquals(Thread.currentThread().getName(), registry.recent(1).get(0).threadName());
    }

    @Test
    @DisplayName("并发登记：存量恒等于容量、累计守恒（有界不逃逸）")
    void concurrentBoundedConservation() throws Exception {
        int capacity = 50;
        int threads = 8;
        int perThread = 250;
        NPlusOneSuspectRegistry registry = new NPlusOneSuspectRegistry(capacity);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final String tag = "t" + t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        registry.record(tag + "-fp-" + i, "sql", 10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        // 不变式：存量恒等于容量（有界不逃逸）、累计守恒；先完成的线程事件可能被全部淘汰，
        // 但留存中的事件必来自某一生产者线程（指纹带线程标签）
        assertEquals(capacity, registry.size());
        assertEquals((long) threads * perThread, registry.total());
        List<NPlusOneSuspect> recent = registry.recent(capacity);
        assertEquals(capacity, recent.size());
        assertTrue(recent.stream().allMatch(s -> s.fingerprint().startsWith("t")),
                "留存事件应均来自生产者线程");
    }
}
