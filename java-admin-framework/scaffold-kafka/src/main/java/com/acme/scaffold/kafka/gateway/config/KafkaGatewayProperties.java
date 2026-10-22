package com.acme.scaffold.kafka.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka 网关（消息生产侧）配置属性（{@code kafka.gateway}）。
 *
 * <p>默认关闭（{@code enabled=false}）——脚手架在无 broker 环境（本地/CI）不建连，
 * 业务环境显式开启并指向集群。record 构造器绑定，未配置字段取 Java 默认值，
 * {@link #bootstrapServersOrDefault()} / 装配器对 null/0 均有兜底。
 *
 * <p>与消费侧 {@code kafka.ordered-consumer} 同属 {@code kafka.*} 命名空间，统一 Kafka 连接约定。
 *
 * @param enabled           是否启用网关消息总线与 Kafka 连接
 * @param bootstrapServers  Kafka 集群地址（逗号分隔，host:port）
 * @param clientId         producer 客户端标识（可选，便于服务端审计）
 * @param acks             应答级别（{@code "1"} 低延迟 / {@code "all"} 高持久；开启幂等时强制 {@code "all"}）
 * @param compressionType  压缩算法（默认 {@code zstd}，依赖 zstd-jni 运行时）
 * @param lingerMs         批量聚合窗口（ms）
 * @param batchSize        批量字节上限
 * @param retries          发送重试次数（&gt;0 时生效）
 * @param enableIdempotence 是否开启幂等 producer（保序关键，要求 acks=all）
 */
@ConfigurationProperties(prefix = "kafka.gateway")
public record KafkaGatewayProperties(
        boolean enabled,
        String bootstrapServers,
        String clientId,
        String acks,
        String compressionType,
        int lingerMs,
        int batchSize,
        int retries,
        boolean enableIdempotence
) {

    /** 生产环境建议的最小完备配置（仅文档用途）。 */
    public static KafkaGatewayProperties sample() {
        return new KafkaGatewayProperties(
                true,
                "kafka-1:9092,kafka-2:9092,kafka-3:9092",
                "scaffold-kafka-gateway",
                "all",
                "zstd",
                5,
                16 * 1024,
                Integer.MAX_VALUE,
                true);
    }

    /** bootstrap 兜底：未配置时回落 localhost:9092（便于本地快速试跑）。 */
    public String bootstrapServersOrDefault() {
        return (bootstrapServers == null || bootstrapServers.isBlank()) ? "localhost:9092" : bootstrapServers;
    }
}
