package com.acme.scaffold.common.messaging.bus;

/**
 * 生产结果（对应设计稿 §2.3「双写非事务」的结算凭证）。
 *
 * <p>Kafka 生产为异步回调结算，{@code accepted=true} 表示已入队待确认；
 * 失败通过 {@code errorCode}/{@code errorMessage} 显式带回，调用方决定
 * 重试 / 转 DLQ / 计数上报（丢弃不是失败，需独立计数口径，§9）。
 *
 * @param accepted     是否被接受（入队）
 * @param topic        目标 topic
 * @param partition    目标分区（-1 表示未知/未路由）
 * @param offset       分区偏移（-1 表示尚未确认）
 * @param errorCode    错误码（成功为 null）
 * @param errorMessage 错误描述（成功为 null）
 */
public record ProduceResult(boolean accepted, String topic, int partition, long offset,
                            String errorCode, String errorMessage) {

    public static ProduceResult accepted(String topic, int partition) {
        return new ProduceResult(true, topic, partition, -1L, null, null);
    }

    public static ProduceResult confirmed(String topic, int partition, long offset) {
        return new ProduceResult(true, topic, partition, offset, null, null);
    }

    public static ProduceResult rejected(String topic, String errorCode, String errorMessage) {
        return new ProduceResult(false, topic, -1, -1L, errorCode, errorMessage);
    }

    public boolean isConfirmed() {
        return accepted && offset >= 0;
    }
}
