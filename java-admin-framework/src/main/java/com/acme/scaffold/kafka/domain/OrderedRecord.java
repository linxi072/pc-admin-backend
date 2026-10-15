package com.acme.scaffold.kafka.domain;

/**
 * 有序消费记录（领域模型）。
 *
 * <p>由 {@code com.acme.scaffold.kafka.port.RecordSource} 从 Kafka 原始记录转换而来，
 * 对业务处理层屏蔽 Kafka API。字段选取遵循「保序三要素」：
 * <ul>
 *   <li>{@code key}：业务唯一键——同 key 经 murmur2 稳定落同一分区，是保序的根基；</li>
 *   <li>{@code partition}/{@code offset}：分区内位次，用于观测与对账；</li>
 *   <li>{@code version}：业务版本号——消费端「严格大于才处理」的乱序兜底依据
 *       （见 {@code com.acme.scaffold.kafka.rules.OrderingRules#shouldProcess}）。</li>
 * </ul>
 *
 * @param topic     主题
 * @param partition 分区号
 * @param offset    分区内位次
 * @param key       message key（业务唯一键；Kafka 允许为 null）
 * @param timestamp 消息时间戳（生产者写入或 broker 接收时间，毫秒）
 * @param version   业务版本号（null 表示无版本信息——保守放行，由业务幂等兜底）
 * @param payload   业务负载（JSON 字符串，由处理方解析）
 */
public record OrderedRecord(
        String topic,
        int partition,
        long offset,
        String key,
        long timestamp,
        Long version,
        String payload
) {

    /**
     * 该记录是否有版本信息。
     *
     * @return true 表示携带业务版本号
     */
    public boolean hasVersion() {
        return version != null;
    }
}
