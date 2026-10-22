package com.acme.scaffold.kafka.gateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KafkaGatewayProperties} 配置属性测试（默认值与兜底）。
 */
class KafkaGatewayPropertiesTest {

    @Test
    void 默认构造_关闭且兜底localhost() {
        // 模拟 Spring 未绑定任何字段时的 Java 默认值
        KafkaGatewayProperties p = new KafkaGatewayProperties(
                false, null, null, null, null, 0, 0, 0, false);
        assertFalse(p.enabled());
        assertEquals("localhost:9092", p.bootstrapServersOrDefault());
        assertFalse(p.enableIdempotence());
    }

    @Test
    void sample_启用且acksAll幂等() {
        KafkaGatewayProperties p = KafkaGatewayProperties.sample();
        assertTrue(p.enabled());
        assertEquals("all", p.acks());
        assertTrue(p.enableIdempotence());
        assertEquals("zstd", p.compressionType());
        assertEquals("kafka-1:9092,kafka-2:9092,kafka-3:9092", p.bootstrapServers());
    }

    @Test
    void bootstrapServersOrDefault_空值回落() {
        KafkaGatewayProperties blank = new KafkaGatewayProperties(
                true, "  ", "c", "all", "zstd", 5, 16384, 3, true);
        assertEquals("localhost:9092", blank.bootstrapServersOrDefault());
        KafkaGatewayProperties set = new KafkaGatewayProperties(
                true, "broker:9092", "c", "all", "zstd", 5, 16384, 3, true);
        assertEquals("broker:9092", set.bootstrapServersOrDefault());
    }
}
