package com.acme.scaffold.kafka.gateway.config;

import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.kafka.gateway.KafkaBus;
import com.acme.scaffold.kafka.gateway.admin.TopicAdmin;
import com.acme.scaffold.kafka.gateway.bus.KafkaRecordSender;
import com.acme.scaffold.kafka.gateway.bus.RecordSender;
import com.acme.scaffold.kafka.gateway.topology.Topology;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Kafka 网关装配（消息生产侧，{@code kafka.gateway}）。
 *
 * <p>门禁：{@code kafka.gateway.enabled=true} 时才注册 {@link MessageBus}（{@link KafkaBus}）与
 * {@link TopicAdmin}——默认关闭使脚手架在无 Kafka 集群的环境（本地/CI）正常启动。
 *
 * <p>拓扑为单一事实来源（{@link Topology#designDefault(int, int)}，144 分区 / 64 扇出分片）；
 * producer 经 {@link KafkaGatewayProperties} 统一参数，字节序列化（网关帧已是二进制，无需二次编码）。
 *
 * <p>幂等约束：Kafka 规定 {@code enable.idempotence=true} 必须配合 {@code acks=all}，配置器在开启幂等时
 * 强制 acks=all 并补足重试，避免 producer 构造期抛出 {@code InvalidOperationException}（§2.3）。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KafkaGatewayProperties.class)
public class KafkaGatewayConfig {

    private static final Topology TOPOLOGY = Topology.designDefault(144, 64);

    /**
     * 分级投递总线（{@link MessageBus} 的 Kafka 生产实现）。
     *
     * @param props 网关配置属性
     * @return 消息总线
     */
    @Bean
    @ConditionalOnProperty(prefix = "kafka.gateway", name = "enabled", havingValue = "true")
    public MessageBus kafkaMessageBus(KafkaGatewayProperties props) {
        KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(producerProperties(props));
        RecordSender sender = new KafkaRecordSender(producer);
        KafkaBus bus = new KafkaBus(TOPOLOGY, sender);
        log.info("Kafka 网关消息总线已装配: bootstrapServers={}, topicSpecs={}, compression={}, idempotent={}",
                props.bootstrapServersOrDefault(), TOPOLOGY.allSpecs().size(),
                props.compressionType(), props.enableIdempotence());
        return bus;
    }

    /**
     * Topic 管理（幂等建 topic，调用方按需 {@code ensureTopics()}）。
     *
     * @param props 网关配置属性
     * @return topic 管理
     */
    @Bean
    @ConditionalOnProperty(prefix = "kafka.gateway", name = "enabled", havingValue = "true")
    public TopicAdmin kafkaTopicAdmin(KafkaGatewayProperties props) {
        Properties adminCfg = new Properties();
        adminCfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServersOrDefault());
        AdminClient admin = AdminClient.create(adminCfg);
        return new TopicAdmin(admin, TOPOLOGY);
    }

    static Properties producerProperties(KafkaGatewayProperties props) {
        boolean idempotent = props.enableIdempotence();
        // 幂等必须 acks=all；否则 KafkaProducer 构造期抛 InvalidOperationException
        String acks = coalesce(props.acks(), "all");
        if (idempotent && !"all".equals(acks)) {
            acks = "all";
        }
        int retries = props.retries() > 0 ? props.retries() : (idempotent ? Integer.MAX_VALUE : 3);

        Properties cfg = new Properties();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServersOrDefault());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArraySerializer");
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.ByteArraySerializer");
        cfg.put(ProducerConfig.ACKS_CONFIG, acks);
        cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, coalesce(props.compressionType(), "zstd"));
        cfg.put(ProducerConfig.LINGER_MS_CONFIG, props.lingerMs() > 0 ? props.lingerMs() : 5);
        cfg.put(ProducerConfig.BATCH_SIZE_CONFIG, props.batchSize() > 0 ? props.batchSize() : 16 * 1024);
        cfg.put(ProducerConfig.RETRIES_CONFIG, retries);
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, idempotent);
        if (props.clientId() != null && !props.clientId().isBlank()) {
            cfg.put(ProducerConfig.CLIENT_ID_CONFIG, props.clientId());
        }
        return cfg;
    }

    private static String coalesce(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
