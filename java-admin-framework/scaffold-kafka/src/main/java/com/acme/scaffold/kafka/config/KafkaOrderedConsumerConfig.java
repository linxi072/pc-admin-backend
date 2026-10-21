package com.acme.scaffold.kafka.config;

import com.acme.scaffold.kafka.adapter.KafkaRecordSource;
import com.acme.scaffold.kafka.domain.KafkaOrderedConsumerProperties;
import com.acme.scaffold.kafka.port.RecordSource;
import com.acme.scaffold.kafka.service.OrderedKafkaConsumer;
import com.acme.scaffold.kafka.service.OrderedRecordHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 有序消费装配。
 *
 * <p>门禁：{@code acme.kafka.ordered-consumer.enabled=true} 时才注册
 * {@link RecordSource} 与 {@link OrderedKafkaConsumer}——默认关闭使脚手架在
 * 无 Kafka 集群的环境（本地/CI）正常启动。业务处理器经 {@link OrderedRecordHandler}
 * 注入：框架内置 {@code LoggingOrderedRecordHandler} 作为兜底，业务方实现同名接口
 * 并标注 {@code @Primary}（或移除默认实现）即可接管。
 */
@Configuration
@EnableConfigurationProperties(KafkaOrderedConsumerProperties.class)
public class KafkaOrderedConsumerConfig {

    /**
     * Kafka 拉取源（仅在启用门禁下创建，构造时完成订阅）。
     *
     * @param properties 配置属性
     * @return 记录源
     */
    @Bean
    @ConditionalOnProperty(prefix = "acme.kafka.ordered-consumer", name = "enabled", havingValue = "true")
    public RecordSource kafkaRecordSource(KafkaOrderedConsumerProperties properties) {
        return new KafkaRecordSource(properties);
    }

    /**
     * 有序消费容器（SmartLifecycle，随容器启停）。
     *
     * @param recordSource        记录源
     * @param handlerProvider     业务处理器提供者（默认日志实现兜底）
     * @param properties          配置属性
     * @return 容器
     */
    @Bean
    @ConditionalOnProperty(prefix = "acme.kafka.ordered-consumer", name = "enabled", havingValue = "true")
    public OrderedKafkaConsumer orderedKafkaConsumer(RecordSource recordSource,
                                                     ObjectProvider<OrderedRecordHandler> handlerProvider,
                                                     KafkaOrderedConsumerProperties properties) {
        OrderedRecordHandler handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            throw new IllegalStateException(
                    "未找到 OrderedRecordHandler 实现（框架默认提供 LoggingOrderedRecordHandler，"
                            + "业务方亦可实现同名接口并标注 @Primary 接管）");
        }
        return new OrderedKafkaConsumer(recordSource, handler, properties);
    }
}
