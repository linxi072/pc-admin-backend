package com.acme.scaffold.kafka.adapter;

import com.acme.scaffold.kafka.domain.KafkaOrderedConsumerProperties;
import com.acme.scaffold.kafka.domain.OrderedRecord;
import com.acme.scaffold.kafka.port.RecordSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Kafka 有序记录拉取源（{@link RecordSource} 的生产实现）。
 *
 * <p>保序相关的 consumer 配置（与容器配合）：
 * <ul>
 *   <li>{@code enable.auto.commit=false}——位移由容器在整批处理后手动
 *       {@link #commitSync()}，确保「处理成功才提交」；</li>
 *   <li>{@code auto.offset.reset=latest}——新组从最新位次开始（海量历史不回灌）；</li>
 *   <li>{@code max.poll.records}——限制单批大小，防 poll 间隔超时引发 rebalance；</li>
 *   <li>String key/value 反序列化——业务负载以 JSON 字符串进入领域模型；</li>
 *   <li>version 从消息 header {@code x-version} 读取（Long 十进制字符串），
 *       生产端约定写入；缺失则按无版本处理（业务幂等兜底）。</li>
 * </ul>
 *
 * <p>线程约定：非线程安全——仅允许容器单线程调用（与 KafkaConsumer 自身约束一致）。
 */
@Slf4j
public class KafkaRecordSource implements RecordSource, Closeable {

    /** 业务版本号消息头（生产端约定写入 Long 十进制字符串） */
    public static final String HEADER_VERSION = "x-version";

    private final KafkaConsumer<String, String> consumer;
    private final Duration pollTimeout;

    /**
     * 构造并订阅 Kafka 消费者。
     *
     * @param props 配置属性（bootstrapServers/groupId/topic 必须非空）
     * @throws IllegalStateException 关键配置缺失或连接失败时
     */
    public KafkaRecordSource(KafkaOrderedConsumerProperties props) {
        if (props.bootstrapServers() == null || props.bootstrapServers().isBlank()) {
            throw new IllegalStateException("kafka.ordered-consumer.bootstrap-servers 未配置");
        }
        if (props.groupId() == null || props.groupId().isBlank()) {
            throw new IllegalStateException("kafka.ordered-consumer.group-id 未配置");
        }
        if (props.topic() == null || props.topic().isBlank()) {
            throw new IllegalStateException("kafka.ordered-consumer.topic 未配置");
        }
        this.pollTimeout = props.pollTimeout() == null ? Duration.ofMillis(100) : props.pollTimeout();

        Properties cfg = new Properties();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers());
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, props.groupId());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        // 保序关键：关闭自动提交，由容器整批处理后手动提交
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // 海量数据：新组不回灌历史；单批限量防 rebalance
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        if (props.maxPollRecords() > 0) {
            cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(props.maxPollRecords()));
        }
        this.consumer = new KafkaConsumer<>(cfg);
        this.consumer.subscribe(Collections.singletonList(props.topic()));
        log.info("Kafka 有序记录源已订阅: topic={}, group={}, maxPollRecords={}",
                props.topic(), props.groupId(), props.maxPollRecords());
    }

    @Override
    public List<OrderedRecord> poll(Duration timeout) {
        ConsumerRecords<String, String> records = consumer.poll(timeout == null ? pollTimeout : timeout);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<OrderedRecord> out = new ArrayList<>(records.count());
        for (ConsumerRecord<String, String> r : records) {
            out.add(new OrderedRecord(
                    r.topic(),
                    r.partition(),
                    r.offset(),
                    r.key(),
                    r.timestamp(),
                    parseVersion(r),
                    r.value()));
        }
        return out;
    }

    @Override
    public void commitSync() {
        consumer.commitSync();
    }

    @Override
    public void close() {
        try {
            consumer.close();
        } catch (RuntimeException ex) {
            log.warn("关闭 Kafka 消费者失败: {}", ex.getMessage());
        }
    }

    /**
     * 从消息头解析业务版本号（{@code x-version}，Long 十进制字符串）。
     *
     * @param record Kafka 原始记录
     * @return 版本号；缺失或解析失败时返回 null（按无版本处理）
     */
    private static Long parseVersion(ConsumerRecord<String, String> record) {
        if (record.headers() == null) {
            return null;
        }
        var header = record.headers().lastHeader(HEADER_VERSION);
        if (header == null || header.value() == null) {
            return null;
        }
        try {
            return Long.parseLong(new String(header.value(), java.nio.charset.StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException ex) {
            log.warn("版本头解析失败，按无版本处理: offset={}, header={}", record.offset(), HEADER_VERSION);
            return null;
        }
    }
}
