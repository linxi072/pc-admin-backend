package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Session} 线程安全单测：Spring WebSocket 下收帧线程与业务推送线程并发访问同一会话，
 * seq 分配必须不重不漏、ACK 水位必须单调不退、closed 标记必须可见。
 */
@DisplayName("Session 并发安全")
class SessionTest {

    private Session newSession() {
        return new Session("c1", "1", 0L, 1_000_000L, new FakeConnChannel("c1", "1"),
                new RateLimiter(100.0, 500.0, new MutableClock(1_000_000L)));
    }

    @Test
    @DisplayName("并发 nextSeq：N 线程各 M 次分配，结果恰好为 1..N*M 不重不漏")
    void concurrentNextSeq_noDuplicates() throws Exception {
        Session session = newSession();
        int threads = 8;
        int perThread = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Long> seqs = Collections.synchronizedSet(new HashSet<>());

        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        seqs.add(session.nextSeq());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        int expected = threads * perThread;
        assertEquals(expected, seqs.size(), "seq 不得重复或丢失");
        assertEquals(1L, seqs.stream().mapToLong(Long::longValue).min().orElse(-1));
        assertEquals((long) expected, seqs.stream().mapToLong(Long::longValue).max().orElse(-1));
    }

    @Test
    @DisplayName("并发 markAcked：随机顺序推进，最终水位为最大值且单调不退")
    void concurrentMarkAcked_monotonic() throws Exception {
        Session session = newSession();
        int n = 2_000;
        int[] values = IntStream.range(1, n + 1).toArray();
        // 打乱后乱序 ACK
        java.util.Random rnd = new java.util.Random(42);
        for (int i = values.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(values.length);
        for (int v : values) {
            pool.execute(() -> {
                session.markAcked(v);
                done.countDown();
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(n, session.lastAckedSeq());
        // 回绕重放不得降低水位
        session.markAcked(5);
        assertEquals(n, session.lastAckedSeq());
    }

    @Test
    @DisplayName("markClosed 后 isClosed 对所有线程可见")
    void closedVisibility() throws Exception {
        Session session = newSession();
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                for (int i = 0; i < 100_000 && !session.isClosed(); i++) {
                    // 自旋等待可见性兑现
                }
                done.countDown();
            });
        }
        session.markClosed();
        assertTrue(done.await(5, TimeUnit.SECONDS), "volatile 写必须被其他线程及时观察到");
        pool.shutdown();
    }
}
