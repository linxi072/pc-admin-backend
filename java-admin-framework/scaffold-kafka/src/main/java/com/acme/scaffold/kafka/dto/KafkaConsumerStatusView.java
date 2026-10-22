package com.acme.scaffold.kafka.dto;

/**
 * 有序消费容器运行态视图（只读聚合，经 {@code KafkaMonitorService} 从 {@code OrderedKafkaConsumer} 采集）。
 *
 * <p>容器由 {@code kafka.ordered-consumer.enabled} 门禁控制：未启用时消费者 Bean 不注册，
 * 此时 {@code enabled=false} 且其余计数均为 0。</p>
 *
 * @param enabled                消费容器是否已装配（配置启用且 Bean 已注册）
 * @param running                容器是否处于运行态（worker 线程存活）
 * @param handledCount           累计已处理记录数
 * @param staleCount             因旧版本/重复被丢弃的记录数
 * @param poisonCount            累计被判定的毒消息数
 * @param poisonPending          当前死信存储中待补偿的毒消息数
 * @param commitFailureCount     位移提交失败次数
 * @param versionTableSize       版本表当前条目数（按 key 维度）
 */
public record KafkaConsumerStatusView(
        boolean enabled,
        boolean running,
        long handledCount,
        long staleCount,
        long poisonCount,
        int poisonPending,
        long commitFailureCount,
        int versionTableSize
) {
}
