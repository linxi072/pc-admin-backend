package com.acme.scaffold.observability.recorder;

import com.acme.scaffold.observability.export.ObservabilityProperties;
import com.acme.scaffold.observability.export.OtlpSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SpanRecorder} 单元测试：有界环形缓冲、倒序检索、traceId 聚合、状态统计、
 * 容量淘汰、禁用语义与并发安全。纯内存组件，无需 Spring 容器。
 */
@DisplayName("SpanRecorder 单元测试")
class SpanRecorderTest {

    private static SpanRecorder recorder(int capacity) {
        ObservabilityProperties props = new ObservabilityProperties();
        props.getRecorder().setCapacity(capacity);
        return new SpanRecorder(props);
    }

    private static OtlpSpan span(String traceId, OtlpSpan.StatusCode status) {
        return new OtlpSpan(traceId, "s-" + traceId + "-" + status, null,
                "GET /x", OtlpSpan.Kind.SERVER, 1_700_000_000_000_000_000L,
                1_700_000_000_050_000_000L, List.of(), status, null);
    }

    @Test
    @DisplayName("记录后 recent 按入队时间倒序返回（最新在前）")
    void recent_returnsNewestFirst() {
        SpanRecorder recorder = recorder(10);
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));
        recorder.record(span("t3", OtlpSpan.StatusCode.OK));

        List<OtlpSpan> recent = recorder.recent(10);
        assertEquals(3, recent.size());
        assertEquals("t3", recent.get(0).traceId);
        assertEquals("t2", recent.get(1).traceId);
        assertEquals("t1", recent.get(2).traceId);
    }

    @Test
    @DisplayName("recent 的 limit 大于容量时按容量截断，limit 小于 1 时按 1 处理")
    void recent_limitClamped() {
        SpanRecorder recorder = recorder(3);
        for (int i = 1; i <= 5; i++) {
            recorder.record(span("t" + i, OtlpSpan.StatusCode.OK));
        }
        // 容量 3：仅留存 t3/t4/t5
        assertEquals(3, recorder.size());
        List<OtlpSpan> all = recorder.recent(100);
        assertEquals(List.of("t5", "t4", "t3"), all.stream().map(s -> s.traceId).toList());
        assertEquals(1, recorder.recent(0).size());
    }

    @Test
    @DisplayName("超出容量后按入队序淘汰最旧记录")
    void capacity_evictsOldest() {
        SpanRecorder recorder = recorder(2);
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));
        recorder.record(span("t3", OtlpSpan.StatusCode.OK));

        assertEquals(2, recorder.size());
        List<OtlpSpan> recent = recorder.recent(2);
        assertEquals("t3", recent.get(0).traceId);
        assertEquals("t2", recent.get(1).traceId);
    }

    @Test
    @DisplayName("byTraceId 按入队序（时间正序）返回同一追踪的全部 span")
    void byTraceId_keepsInsertionOrder() {
        SpanRecorder recorder = recorder(10);
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));
        recorder.record(span("t1", OtlpSpan.StatusCode.ERROR));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));

        List<OtlpSpan> t1 = recorder.byTraceId("t1");
        assertEquals(2, t1.size());
        assertEquals(OtlpSpan.StatusCode.OK, t1.get(0).status);
        assertEquals(OtlpSpan.StatusCode.ERROR, t1.get(1).status);
        assertTrue(recorder.byTraceId("missing").isEmpty());
    }

    @Test
    @DisplayName("stats 按状态累计计数（含已被淘汰的历史条数）")
    void stats_countsByStatus() {
        SpanRecorder recorder = recorder(2);
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.ERROR));
        recorder.record(span("t3", OtlpSpan.StatusCode.UNSET));

        SpanRecorder.RecorderStats stats = recorder.stats();
        assertTrue(stats.enabled());
        assertEquals(2, stats.capacity());
        assertEquals(2, stats.size());
        assertEquals(1L, stats.okCount());
        assertEquals(1L, stats.errorCount());
        assertEquals(1L, stats.unsetCount());
    }

    @Test
    @DisplayName("clear 返回清除条数并重置计数")
    void clear_resetsCounts() {
        SpanRecorder recorder = recorder(10);
        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        recorder.record(span("t2", OtlpSpan.StatusCode.OK));

        assertEquals(2, recorder.clear());
        assertEquals(0, recorder.size());
        SpanRecorder.RecorderStats stats = recorder.stats();
        assertEquals(0L, stats.okCount());
        assertEquals(0L, stats.errorCount());
        assertEquals(0L, stats.unsetCount());
    }

    @Test
    @DisplayName("禁用时 record 静默忽略，recent/byTraceId/stats 均为空态")
    void disabled_recordsNothing() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.getRecorder().setEnabled(false);
        SpanRecorder recorder = new SpanRecorder(props);

        recorder.record(span("t1", OtlpSpan.StatusCode.OK));
        assertEquals(0, recorder.size());
        assertTrue(recorder.recent(10).isEmpty());
        assertTrue(recorder.byTraceId("t1").isEmpty());
        SpanRecorder.RecorderStats stats = recorder.stats();
        assertTrue(!stats.enabled());
        assertEquals(0L, stats.okCount());
    }

    @Test
    @DisplayName("export(List) 批量录入（SpanExporter 适配），空列表安全忽略")
    void export_batchRecordsAll() {
        SpanRecorder recorder = recorder(10);
        recorder.export(List.of(span("t1", OtlpSpan.StatusCode.OK), span("t2", OtlpSpan.StatusCode.OK)));
        recorder.export(List.of());
        recorder.export(null);
        assertEquals(2, recorder.size());
        assertEquals(2L, recorder.stats().okCount());
    }

    @Test
    @DisplayName("record(null) 安全忽略，不影响计数")
    void record_nullSafe() {
        SpanRecorder recorder = recorder(10);
        recorder.record(null);
        assertEquals(0, recorder.size());
        SpanRecorder.RecorderStats stats = recorder.stats();
        assertEquals(0L, stats.okCount() + stats.errorCount() + stats.unsetCount());
    }

    @Test
    @DisplayName("并发 record：留存不超容量且统计守恒（总数 = ok+error+unset）")
    void concurrent_recordsBoundedAndConsistent() throws Exception {
        SpanRecorder recorder = recorder(50);
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        recorder.record(span("t" + j, OtlpSpan.StatusCode.OK));
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

        assertNotNull(recorder.stats());
        assertTrue(recorder.size() <= 50, "留存不得超出容量");
        SpanRecorder.RecorderStats stats = recorder.stats();
        assertEquals(threads * perThread, stats.okCount() + stats.errorCount() + stats.unsetCount());
    }
}
