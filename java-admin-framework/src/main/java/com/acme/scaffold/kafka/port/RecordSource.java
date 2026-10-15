package com.acme.scaffold.kafka.port;

import com.acme.scaffold.kafka.domain.OrderedRecord;

import java.time.Duration;
import java.util.List;

/**
 * 有序记录拉取源（端口）。
 *
 * <p>将 {@code org.apache.kafka.clients.consumer.KafkaConsumer} 的 poll/commit 能力抽象为
 * 领域端口：生产实现为 Kafka 适配器（见
 * {@code com.acme.scaffold.kafka.config.KafkaOrderedConsumerConfig#kafkaRecordSource}），
 * 测试以内存假实现注入——容器 {@code OrderedKafkaConsumer} 因此可在无 broker 环境下完整单测。
 *
 * <p>实现约定（保序关键）：
 * <ul>
 *   <li>{@link #poll} 返回的记录必须保持分区内 offset 升序（Kafka 单 poll 天然保证）；</li>
 *   <li>{@link #commitSync} 必须同步提交——仅当调用方确认「整批已处理」时才会被调用，
 *       失败时调用方放弃提交使下轮重投（at-least-once + 版本去重兜底）。</li>
 * </ul>
 */
public interface RecordSource {

    /**
     * 拉取一批记录（阻塞至多 {@code timeout}）。
     *
     * @param timeout 最长阻塞时长（null 视为 100ms）
     * @return 记录列表（空列表表示无新记录；不为 null）
     */
    List<OrderedRecord> poll(Duration timeout);

    /**
     * 同步提交当前已处理的位移。
     *
     * <p>对应 {@code KafkaConsumer#commitSync()}；提交失败时实现应抛出运行时异常，
     * 由容器决定重试策略（默认：记录错误并放弃本批，下轮重投）。
     */
    void commitSync();

    /**
     * 关闭源、释放连接（幂等）。
     */
    default void close() {
    }
}
