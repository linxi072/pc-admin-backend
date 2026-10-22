package com.acme.scaffold.kafka.gateway.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link KafkaGatewayConfig#producerProperties} 单元测试（无需 broker：仅构建 Properties）。
 *
 * <p>重点验证幂等约束：Kafka 规定 {@code enable.idempotence=true} 必须配合 {@code acks=all}，
 * 配置器在开启幂等时强制 acks=all 并补足重试，避免 producer 构造期抛 InvalidOperationException。
 *
 * <p>注意：producer 参数以 Kafka 原生类型（Boolean/Integer/String）存入 Properties，
 * 与 {@code KafkaProducer} 的强类型配置契约一致；读取断言使用 {@code cfg.get(key)} 而非
 * {@code getProperty}（后者仅返回 String）。
 */
class KafkaGatewayConfigTest {

    @Test
    void producerProperties_幂等强制acksAll且重试封顶() {
        // 用户误配 acks=1 + 开启幂等 + 未设 retries
        KafkaGatewayProperties p = new KafkaGatewayProperties(
                true, "broker:9092", "gw", "1", "zstd", 5, 16384, 0, true);
        Properties cfg = KafkaGatewayConfig.producerProperties(p);

        assertEquals("all", cfg.getProperty(ProducerConfig.ACKS_CONFIG));
        assertEquals(Boolean.TRUE, cfg.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), cfg.get(ProducerConfig.RETRIES_CONFIG));
        assertEquals("org.apache.kafka.common.serialization.ByteArraySerializer",
                cfg.getProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals("org.apache.kafka.common.serialization.ByteArraySerializer",
                cfg.getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("broker:9092", cfg.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("gw", cfg.getProperty(ProducerConfig.CLIENT_ID_CONFIG));
    }

    @Test
    void producerProperties_非幂等保留acks1且重试兜底3() {
        KafkaGatewayProperties p = new KafkaGatewayProperties(
                true, "broker:9092", null, "1", "zstd", 0, 0, 0, false);
        Properties cfg = KafkaGatewayConfig.producerProperties(p);

        assertEquals("1", cfg.getProperty(ProducerConfig.ACKS_CONFIG));
        assertEquals(Boolean.FALSE, cfg.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(Integer.valueOf(3), cfg.get(ProducerConfig.RETRIES_CONFIG));
        // 未配置的 linger/batch 回落内置默认值
        assertEquals(Integer.valueOf(5), cfg.get(ProducerConfig.LINGER_MS_CONFIG));
        assertEquals(Integer.valueOf(16 * 1024), cfg.get(ProducerConfig.BATCH_SIZE_CONFIG));
        // clientId 缺失则不写入
        assertEquals(null, cfg.getProperty(ProducerConfig.CLIENT_ID_CONFIG));
    }

    @Test
    void producerProperties_压缩算法缺省zstd() {
        KafkaGatewayProperties p = new KafkaGatewayProperties(
                true, "broker:9092", null, "all", null, 5, 16384, 3, true);
        Properties cfg = KafkaGatewayConfig.producerProperties(p);
        assertEquals("zstd", cfg.getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG));
    }

    @Test
    void producerProperties_显式acksAll被保留() {
        KafkaGatewayProperties p = KafkaGatewayProperties.sample(); // acks=all, idempotence=true, retries=MAX
        Properties cfg = KafkaGatewayConfig.producerProperties(p);
        assertEquals("all", cfg.getProperty(ProducerConfig.ACKS_CONFIG));
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), cfg.get(ProducerConfig.RETRIES_CONFIG));
    }
}
