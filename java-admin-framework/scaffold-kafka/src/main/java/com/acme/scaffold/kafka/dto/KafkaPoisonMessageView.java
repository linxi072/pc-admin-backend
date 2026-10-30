package com.acme.scaffold.kafka.dto;

/**
 * 毒消息（死信）视图（REST 出参，由 {@code PoisonMessage} 领域对象经 {@code KafkaMonitorService} 映射得到）。
 *
 * @param id                     稳定标识，用于补偿操作路径定位
 * @param topic                  Kafka 主题
 * @param partition              分区号
 * @param offset                 分区内位次
 * @param key                    message key（可能为空）
 * @param payloadPreview         负载预览（截断）
 * @param attempts               失败次数
 * @param lastError              末次失败异常信息
 * @param detectedAtEpochMillis  判定为毒消息的时间戳（毫秒）
 */
public record KafkaPoisonMessageView(
        String id,
        String topic,
        int partition,
        long offset,
        String key,
        String payloadPreview,
        int attempts,
        String lastError,
        long detectedAtEpochMillis
) {
}
