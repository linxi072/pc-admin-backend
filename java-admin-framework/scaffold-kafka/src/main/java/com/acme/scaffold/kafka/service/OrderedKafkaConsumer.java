package com.acme.scaffold.kafka.service;

import com.acme.scaffold.kafka.domain.KafkaOrderedConsumerProperties;
import com.acme.scaffold.kafka.domain.OrderedRecord;
import com.acme.scaffold.kafka.domain.PoisonMessage;
import com.acme.scaffold.kafka.port.RecordSource;
import com.acme.scaffold.kafka.rules.OrderingRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 海量数据有序消费容器（SmartLifecycle）。
 *
 * <p>保序机制（自底向上）：
 * <ol>
 *   <li><b>分区内串行</b>：单个 worker 线程顺序处理 poll 批内记录——Kafka 单 poll 返回的
 *       同一分区记录按 offset 升序，顺序处理即保证分区内 FIFO；跨分区记录互不相干，
 *       无需全局排序（同 key 必落同一分区，天然归同一实例处理）；</li>
 *   <li><b>版本去重</b>：以 {@code key -> 已处理最大版本} 内存表执行
 *       {@link OrderingRules#shouldProcess}——乱序迟到的旧版本/重复投递直接丢弃，
 *       仅严格更新的版本进入业务处理器（针对「数据频繁更新」的乱序兜底）；</li>
 *   <li><b>手动提交</b>：整批处理完毕才 {@link RecordSource#commitSync()}——
 *       中途异常放弃提交，下轮重投（at-least-once），重复记录再被版本表挡下；</li>
 *   <li><b>背压控制</b>：单批记录数与 poll 时长由
 *       {@code com.acme.scaffold.kafka.domain.KafkaOrderedConsumerProperties} 约束，
 *       防止 max.poll.interval.ms 超时引发 rebalance。</li>
 * </ol>
 *
 * <p>生命周期：随 Spring 容器启动/停止；停止时中断 worker 线程并关闭 {@link RecordSource}。
 * 内存版本表在重启后清零——依赖「重启后首次全量重投 + 版本严格大于」恢复一致性
 * （首次见到该 key 一律处理）。
 *
 * <p>装配：由 {@code com.acme.scaffold.kafka.config.KafkaOrderedConsumerConfig} 以
 * {@code @Bean} 创建并受 {@code kafka.ordered-consumer.enabled=true} 门禁
 * （未启用时不注册，避免无 broker 环境下启动失败）。
 */
@Slf4j
public class OrderedKafkaConsumer implements SmartLifecycle, DisposableBean {

    /** poll 默认阻塞时长（属性未配置时） */
    private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(100);

    private final RecordSource recordSource;
    private final OrderedRecordHandler recordHandler;
    private final KafkaOrderedConsumerProperties properties;

    /** key -> 已处理的最大业务版本（仅记录有版本号的消息；无版本 key 不占位） */
    private final Map<String, Long> processedVersions = new ConcurrentHashMap<>();

    /**
     * 单条记录的最大投递次数。
     *
     * <p>达到上限即判定为「毒消息」（poison pill）：本进程内跳过并落 ERROR 日志。历史行为是
     * 「批内失败即放弃提交、下轮原样重投」，若失败是确定性的（如 payload 非法），同一条记录会
     * 无限重投——消费者永久卡死且后续消息全部积压。跳过是「保可用」的取舍：毒消息已完整记录在
     * 日志中，需人工或死信队列补偿。</p>
     */
    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    /** 位点（topic-partition-offset）-> 已投递失败次数 */
    private final Map<String, Integer> deliveryAttempts = new ConcurrentHashMap<>();
    /** 被判定为毒消息而跳过的记录数（观测用）。 */
    private final LongAdder poisonCount = new LongAdder();

    private final LongAdder handledCount = new LongAdder();
    private final LongAdder staleCount = new LongAdder();
    private final LongAdder commitFailureCount = new LongAdder();
    private final AtomicLong lastCommitFailureLogAt = new AtomicLong(0L);
    /** 毒消息（死信）存储：被判定为毒消息而跳过的记录在此留痕，供运维经 REST 查看/补偿。内存态，重启清零。 */
    private final Map<String, PoisonMessage> poisonStore = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile Thread worker;

    /**
     * 构造容器。
     *
     * @param recordSource  记录拉取源（Kafka 适配器或测试假实现）
     * @param recordHandler 业务处理器
     * @param properties    配置属性（不可为 null）
     */
    public OrderedKafkaConsumer(RecordSource recordSource,
                                OrderedRecordHandler recordHandler,
                                KafkaOrderedConsumerProperties properties) {
        this.recordSource = Objects.requireNonNull(recordSource, "recordSource");
        this.recordHandler = Objects.requireNonNull(recordHandler, "recordHandler");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::consumeLoop, "ordered-kafka-consumer");
        worker.setDaemon(false);
        worker.start();
        log.info("有序消费容器已启动: topic={}, handler={}",
                properties.topic(), recordHandler.getClass().getSimpleName());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        Thread t = worker;
        if (t != null) {
            t.interrupt();
            try {
                // 等待 worker 退出当前 poll/批次后再由 destroy() 关闭消费者：
                // KafkaConsumer 非线程安全，poll 与 close 并发属未定义行为。
                // poll 不响应中断，但受 pollTimeout 约束会自行返回，故给足超时后即可退出。
                Duration pollTimeout = properties.pollTimeout() == null ? DEFAULT_POLL_TIMEOUT : properties.pollTimeout();
                t.join(pollTimeout.toMillis() + 30_000);
                if (t.isAlive()) {
                    log.warn("有序消费 worker 未在超时内退出，销毁阶段关闭消费者可能存在竞态");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("有序消费容器已停止: 已处理={}, 丢弃旧版本={}", handledCount.sum(), staleCount.sum());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 停止阶段最后执行（数据库/缓存等停止后再停消费，避免停序期读到半关状态）。
     *
     * @return 整数最大值
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void destroy() {
        stop();
        recordSource.close();
    }

    /**
     * 消费主循环：poll -> 顺序处理 -> 整批提交。
     *
     * <p>异常策略：单条处理失败即中断本批并放弃提交（下轮重投同批，版本表挡重）；
     * 提交失败计数并告警（节流 10s 一条），不影响主循环续跑。
     */
    private void consumeLoop() {
        Duration pollTimeout = properties.pollTimeout() == null ? DEFAULT_POLL_TIMEOUT : properties.pollTimeout();
        while (running && !Thread.currentThread().isInterrupted()) {
            List<OrderedRecord> records;
            try {
                records = recordSource.poll(pollTimeout);
            } catch (RuntimeException ex) {
                log.warn("拉取记录失败，稍后重试: {}", ex.getMessage());
                sleepBriefly();
                continue;
            }
            if (records == null || records.isEmpty()) {
                continue;
            }
            try {
                for (OrderedRecord record : records) {
                    if (!running) {
                        return;
                    }
                    dispatch(record);
                }
            } catch (RuntimeException ex) {
                // 本批放弃提交：Kafka 未提交位移，下轮重投；版本表将挡下已处理的记录
                log.error("批内处理失败，放弃提交等待重投（已处理部分由版本表兜底去重）: topic={}, 异常={}",
                        properties.topic(), ex.toString());
                sleepBriefly();
                continue;
            }
            commitIfDue(records);
        }
    }

    /**
     * 单条分发：版本规则判定 -> 业务处理 -> 版本表推进。
     *
     * @param record 待处理记录
     */
    private void dispatch(OrderedRecord record) {
        Long processed = record.key() == null ? null : processedVersions.get(record.key());
        if (!OrderingRules.shouldProcess(processed, record.version())) {
            staleCount.increment();
            log.debug("丢弃旧版本/重复消息: key={}, 已处理版本={}, 来稿版本={}, offset={}",
                    record.key(), processed, record.version(), record.offset());
            return;
        }
        String site = siteOf(record);
        if (deliveryAttempts.getOrDefault(site, 0) >= MAX_DELIVERY_ATTEMPTS) {
            log.warn("毒消息跳过（已失败 {} 次，避免整批反复重投）: topic={} partition={} offset={} key={}",
                    MAX_DELIVERY_ATTEMPTS, record.topic(), record.partition(), record.offset(), record.key());
            return;
        }
        try {
            recordHandler.handle(record);
        } catch (RuntimeException ex) {
            int attempts = deliveryAttempts.merge(site, 1, Integer::sum);
            if (attempts >= MAX_DELIVERY_ATTEMPTS) {
                poisonCount.increment();
                String pid = UUID.randomUUID().toString();
                poisonStore.put(pid, new PoisonMessage(pid, record.topic(), record.partition(),
                        record.offset(), record.key(), preview(record.payload()), attempts,
                        ex.toString(), System.currentTimeMillis()));
                log.error("记录处理连续失败 {} 次，判定为毒消息（已收入死信存储，可经 /api/kafka/consumer/poison 补偿）: "
                                + "topic={} partition={} offset={} key={} 异常={}",
                        attempts, record.topic(), record.partition(), record.offset(), record.key(), ex.toString());
            }
            // 仍抛出：保留「本批放弃提交、交回上游重投」的既有语义
            throw ex;
        }
        // 处理成功：清理该位点的历史失败计数（位点相同但内容不会重放，计数不应跨批次累积）
        deliveryAttempts.remove(site);
        handledCount.increment();
        if (record.version() != null && record.key() != null) {
            processedVersions.put(record.key(), record.version());
        }
    }

    /** 位点标识：同一条消息重投时 topic/partition/offset 不变，故可作为毒消息计数的键。 */
    private static String siteOf(OrderedRecord record) {
        return siteOf(record.topic(), record.partition(), record.offset());
    }

    /** 位点标识（按字段构造，供补偿时按毒消息元数据反向定位失败计数）。 */
    private static String siteOf(String topic, int partition, long offset) {
        return topic + "-" + partition + "-" + offset;
    }

    /**
     * 整批处理完毕后的同步提交（失败仅计数告警，不抛出——避免与处理失败的语义混淆）。
     *
     * @param records 本批记录（仅用于日志上下文）
     */
    private void commitIfDue(List<OrderedRecord> records) {
        try {
            recordSource.commitSync();
        } catch (RuntimeException ex) {
            commitFailureCount.increment();
            logCommitFailureThrottled(ex, records);
        }
    }

    private void logCommitFailureThrottled(RuntimeException ex, List<OrderedRecord> records) {
        long now = System.currentTimeMillis();
        long last = lastCommitFailureLogAt.get();
        if (now - last >= 10_000L && lastCommitFailureLogAt.compareAndSet(last, now)) {
            log.error("位移提交失败（10s 内仅告警一次，累计 {} 次）: topic={}, 批大小={}, 异常={}",
                    commitFailureCount.sum(), properties.topic(), records.size(), ex.toString());
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(200L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- 运行指标（可观测） ----

    /** @return 已处理记录总数 */
    public long handledCount() {
        return handledCount.sum();
    }

    /** @return 因旧版本/重复被丢弃的记录总数 */
    public long staleCount() {
        return staleCount.sum();
    }

    /** @return 位移提交失败次数 */
    public long commitFailureCount() {
        return commitFailureCount.sum();
    }

    /** @return 版本表当前条目数（按 key 维度） */
    public int processedVersionTableSize() {
        return processedVersions.size();
    }

    /** @return 被判定为毒消息而跳过（累计）的记录总数 */
    public long poisonCount() {
        return poisonCount.sum();
    }

    /**
     * 当前内存死信存储中的毒消息快照（返回副本，遍历安全）。
     *
     * @return 毒消息列表；无则空列表
     */
    public List<PoisonMessage> poisonEntries() {
        return List.copyOf(poisonStore.values());
    }

    /** @return 内存死信存储当前条目数 */
    public int poisonStoreSize() {
        return poisonStore.size();
    }

    /**
     * 补偿：重试某条毒消息。清除其位点失败计数与版本表占位，使上游重投后能被重新处理
     * （消费侧「严格大于才处理」；若重投版本不前进，仍会被版本表挡下，需上游修正后重发）。
     *
     * @param id 毒消息标识（{@link PoisonMessage#id()}）
     * @return 是否找到并清除（未找到返回 false）
     */
    public boolean retryPoison(String id) {
        PoisonMessage removed = poisonStore.remove(id);
        if (removed == null) {
            return false;
        }
        deliveryAttempts.remove(siteOf(removed.topic(), removed.partition(), removed.offset()));
        if (removed.key() != null) {
            processedVersions.remove(removed.key());
        }
        return true;
    }

    /**
     * 补偿：丢弃某条毒消息。从死信存储移除，视为已人工确认、无需再补偿。
     *
     * @param id 毒消息标识
     * @return 是否找到并移除
     */
    public boolean discardPoison(String id) {
        return poisonStore.remove(id) != null;
    }

    /** 负载预览：超长截断，避免大报文占满内存。 */
    private static String preview(String payload) {
        if (payload == null) {
            return "";
        }
        return payload.length() <= 200 ? payload : payload.substring(0, 200) + "...(truncated)";
    }
}
