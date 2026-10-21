package com.acme.scaffold.kafka.domain;

/**
 * 毒消息（死信）记录。
 *
 * <p>当某条有序记录在达到最大投递次数（{@code OrderedKafkaConsumer#MAX_DELIVERY_ATTEMPTS}）后仍处理失败，
 * 消费者将其判定为毒消息并跳过，同时捕获为 {@link PoisonMessage} 存入内存死信存储，供运维通过 REST 接口
 * 查看与补偿（重试 / 丢弃）。内存态，容器重启后清零——与既有版本表「重启即全量重投」的语义一致。</p>
 *
 * @param id                     稳定标识（UUID），用于 REST 路径定位单条毒消息
 * @param topic                  Kafka 主题
 * @param partition              分区号
 * @param offset                 分区内位次
 * @param key                    message key（业务唯一键；Kafka 允许为 null，此时为空字符串）
 * @param payloadPreview         负载预览（截断至 200 字符，避免大报文占满内存）
 * @param attempts               失败次数（达到上限 {@code MAX_DELIVERY_ATTEMPTS}）
 * @param lastError              末次失败异常信息（仅类名+消息，敏感细节不入库）
 * @param detectedAtEpochMillis  判定为毒消息的时间戳（毫秒）
 */
public record PoisonMessage(
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
