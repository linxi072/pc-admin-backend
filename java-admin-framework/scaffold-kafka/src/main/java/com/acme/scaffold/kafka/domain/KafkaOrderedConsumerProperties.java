package com.acme.scaffold.kafka.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 有序消费配置属性（{@code kafka.ordered-consumer}）。
 *
 * <p>默认关闭（{@code enabled=false}）——脚手架不在无 broker 环境误连；
 * 业务环境显式开启并指向集群。record 构造器绑定，字段缺失时取 Java 默认值
 * （boolean false / int 0 / 对象 null），容器对 null/0 均有兜底。
 *
 * @param enabled        是否启用有序消费容器与 Kafka 连接
 * @param bootstrapServers Kafka 集群地址（逗号分隔，host:port）
 * @param groupId        消费者组（同组多实例分摊分区；同 key 必落同一分区故仍保序）
 * @param topic          订阅主题
 * @param pollTimeout    单次 poll 最长阻塞（null 时容器默认 100ms）
 * @param maxPollRecords 单批最大记录数（&lt;=0 表示不覆盖 broker/client 默认，靠背压防 rebalance）
 */
@ConfigurationProperties(prefix = "kafka.ordered-consumer")
public record KafkaOrderedConsumerProperties(
        boolean enabled,
        String bootstrapServers,
        String groupId,
        String topic,
        Duration pollTimeout,
        int maxPollRecords
) {

    /**
     * 生产环境建议的最小完备配置（便于快速对照）。
     *
     * @return 示例属性实例（仅文档用途）
     */
    public static KafkaOrderedConsumerProperties sample() {
        return new KafkaOrderedConsumerProperties(
                true,
                "kafka-1:9092,kafka-2:9092,kafka-3:9092",
                "scaffold-ordered-consumer",
                "business.data.updated",
                Duration.ofMillis(200),
                500);
    }
}
