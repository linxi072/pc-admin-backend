package com.acme.scaffold.kafka.gateway.bus;

import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.domain.Tier;

/**
 * Kafka 发送端口：真正的网络发送被隔离在此，便于无 broker 环境下以 fake 注入测试。
 *
 * <p>生产实现 {@link KafkaRecordSender} 包裹 {@code KafkaProducer}；
 * 测试实现为内存记录器（断言 topic/分区/键值选择，§4.1）。
 */
public interface RecordSender {

    /**
     * 发送一条已编码记录。
     *
     * @return 生产结果；总线自身故障返回 rejected 而非抛出（热路径不因 Kafka 故障崩溃）
     */
    ProduceResult send(Tier tier, String topic, int partition, byte[] key, byte[] value);

    /** 同步刷盘（优雅退出第 6 步，§3.6）。 */
    void flush(long timeoutMs);

    /** 关闭底层资源（KafkaProducer 等），幂等。 */
    void close();
}
