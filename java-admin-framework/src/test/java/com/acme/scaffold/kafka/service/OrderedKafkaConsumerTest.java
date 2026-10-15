package com.acme.scaffold.kafka.service;

import com.acme.scaffold.kafka.domain.KafkaOrderedConsumerProperties;
import com.acme.scaffold.kafka.domain.OrderedRecord;
import com.acme.scaffold.kafka.port.RecordSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OrderedKafkaConsumer} 行为测试（内存假源，无 broker 依赖）。
 *
 * <p>覆盖保序四要素的容器侧行为：分区内串行、版本去重丢弃、整批手动提交、
 * 处理异常放弃提交、启停生命周期与运行指标。
 */
class OrderedKafkaConsumerTest {

    /** 可编排批量/提交计数/异常注入的内存假源 */
    static class FakeRecordSource implements RecordSource {

        private final List<List<OrderedRecord>> batches = new ArrayList<>();
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger polls = new AtomicInteger();
        private volatile boolean failCommit = false;
        private int cursor = 0;

        void offerBatch(OrderedRecord... records) {
            batches.add(List.of(records));
        }

        @Override
        public List<OrderedRecord> poll(Duration timeout) {
            polls.incrementAndGet();
            if (cursor >= batches.size()) {
                return List.of();
            }
            return batches.get(cursor++);
        }

        @Override
        public void commitSync() {
            if (failCommit) {
                throw new RuntimeException("模拟提交失败");
            }
            commits.incrementAndGet();
        }
    }

    /** 记录调用顺序的处理器（支持对特定 key 抛异常） */
    static class RecordingHandler implements OrderedRecordHandler {

        final List<String> handled = new CopyOnWriteArrayList<>();
        private final String failOnKey;

        RecordingHandler() {
            this(null);
        }

        RecordingHandler(String failOnKey) {
            this.failOnKey = failOnKey;
        }

        @Override
        public void handle(OrderedRecord record) {
            handled.add(record.key() + "@" + record.version());
            if (failOnKey != null && failOnKey.equals(record.key())) {
                throw new IllegalStateException("模拟处理失败: " + record.key());
            }
        }
    }

    private FakeRecordSource source;
    private OrderedKafkaConsumer consumer;
    private RecordingHandler handler;

    private static final KafkaOrderedConsumerProperties PROPS =
            new KafkaOrderedConsumerProperties(true, "mock:9092", "g", "t", Duration.ofMillis(20), 100);

    @BeforeEach
    void setUp() {
        source = new FakeRecordSource();
        handler = new RecordingHandler();
        consumer = new OrderedKafkaConsumer(source, handler, PROPS);
    }

    @AfterEach
    void tearDown() {
        if (consumer.isRunning()) {
            consumer.stop();
        }
    }

    private static OrderedRecord record(String key, Long version, long offset) {
        return new OrderedRecord("t", 0, offset, key, 0L, version, "payload-" + key);
    }

    /** 轮询等待条件满足（无 Awaitility 依赖的轻量实现） */
    private static void awaitCount(AtomicInteger counter, int expected) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && counter.get() < expected) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void awaitHandled(int count) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && handler.handled.size() < count) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void 启动后按批顺序处理且每批提交一次() {
        source.offerBatch(
                record("A", 1L, 0),
                record("B", 1L, 1),
                record("A", 2L, 2));
        consumer.start();
        awaitHandled(3);
        awaitCount(source.commits, 1);

        assertEquals(List.of("A@1", "B@1", "A@2"), handler.handled);
        assertEquals(3, consumer.handledCount());
        assertEquals(0, consumer.staleCount());
        assertEquals(1, source.commits.get());
    }

    @Test
    void 旧版本与同版本重复投递被丢弃且不影响提交() {
        source.offerBatch(record("A", 5L, 0));
        source.offerBatch(record("A", 3L, 1));   // 旧版本 -> 丢弃
        source.offerBatch(record("A", 5L, 2));   // 同版本 -> 丢弃
        consumer.start();
        awaitHandled(1);
        awaitCount(source.commits, 3);

        assertEquals(List.of("A@5"), handler.handled);
        assertEquals(1, consumer.handledCount());
        assertEquals(2, consumer.staleCount());
        assertEquals(3, source.commits.get(), "非空批即使全部丢弃也应提交位移");
    }

    @Test
    void 处理异常时放弃本批提交() {
        RecordingHandler failing = new RecordingHandler("B");
        OrderedKafkaConsumer c = new OrderedKafkaConsumer(source, failing, PROPS);
        source.offerBatch(record("A", 1L, 0), record("B", 1L, 1), record("A", 2L, 2));
        c.start();
        // 等 worker 至少跑完一个 poll 周期
        awaitCount(source.polls, 1);
        try {
            Thread.sleep(300);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        c.stop();

        // B 的 handle 已被调用（其内部先记账后抛异常——at-least-once 语义下业务须幂等），
        // 但批内第三条 A@2 未被触及
        assertEquals(List.of("A@1", "B@1"), failing.handled, "异常中断批内后续处理");
        assertEquals(0, source.commits.get(), "异常批不得提交");
        assertEquals(1, c.handledCount(), "仅 handle 正常返回才计数");
    }

    @Test
    void 无版本来稿保守放行且不占版本表() {
        source.offerBatch(record("A", null, 0), record("A", null, 1));
        consumer.start();
        awaitHandled(2);

        assertEquals(List.of("A@null", "A@null"), handler.handled);
        assertEquals(0, consumer.processedVersionTableSize(), "无版本 key 不占版本表");
    }

    @Test
    void 版本表按key维度维护() {
        source.offerBatch(
                record("A", 1L, 0), record("A", 2L, 1),
                record("B", 7L, 2));
        consumer.start();
        awaitHandled(3);

        assertEquals(2, consumer.processedVersionTableSize());
    }

    @Test
    void 提交失败仅计数不中断主循环() {
        source.failCommit = true;
        source.offerBatch(record("A", 1L, 0));
        consumer.start();
        awaitHandled(1);
        try {
            Thread.sleep(300);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        assertEquals(1, consumer.handledCount());
        assertTrue(consumer.commitFailureCount() > 0, "提交失败应被计数");
        assertEquals(0, source.commits.get());
    }

    @Test
    void 启动停止生命周期正确() {
        assertFalse(consumer.isRunning());
        consumer.start();
        assertTrue(consumer.isRunning());
        consumer.stop();
        assertFalse(consumer.isRunning());
    }

    @Test
    void 重复启动幂等() {
        consumer.start();
        consumer.start();
        awaitCount(source.polls, 1);
        assertTrue(consumer.isRunning());
        consumer.stop();
    }

    @Test
    void 构造参数校验() {
        // 记录源为空
        assertEquals("recordSource",
                org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                        () -> new OrderedKafkaConsumer(null, handler, PROPS)).getMessage());
        // 处理器为空
        assertEquals("recordHandler",
                org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                        () -> new OrderedKafkaConsumer(source, null, PROPS)).getMessage());
        // 属性为空
        assertEquals("properties",
                org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                        () -> new OrderedKafkaConsumer(source, handler, null)).getMessage());
    }

    @Test
    void 空批不提交() {
        consumer.start();
        awaitCount(source.polls, 2);
        assertEquals(0, source.commits.get(), "空批不应触发提交");
    }
}
