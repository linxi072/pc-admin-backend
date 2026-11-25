package com.acme.scaffold.kafka.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 保序规则单测：murmur2 位级兼容 Kafka 默认分区器（参考值由独立实现交叉计算）、
 * 分区稳定性与版本号乱序兜底。
 */
class OrderingRulesTest {

    @Test
    void murmur2ShouldMatchKafkaReferenceVectors() {
        assertEquals(275646681, OrderingRules.murmur2("".getBytes(StandardCharsets.UTF_8)));
        assertEquals(-798503068, OrderingRules.murmur2("kafka".getBytes(StandardCharsets.UTF_8)));
        assertEquals(696778364, OrderingRules.murmur2("user-7".getBytes(StandardCharsets.UTF_8)));
        assertEquals(528512580, OrderingRules.murmur2("中".getBytes(StandardCharsets.UTF_8)));
        assertEquals(-564775217, OrderingRules.murmur2("order-1234567890".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void murmur2ShouldRejectNullInput() {
        assertThrows(NullPointerException.class, () -> OrderingRules.murmur2(null));
    }

    @Test
    void toPositiveShouldClearSignBit() {
        assertEquals(0, OrderingRules.toPositive(Integer.MIN_VALUE));
        assertEquals(Integer.MAX_VALUE, OrderingRules.toPositive(-1));
        assertEquals(42, OrderingRules.toPositive(42));
    }

    @Test
    void partitionForKeyShouldBeStableAndWithinRange() {
        for (int i = 0; i < 20; i++) {
            int p = OrderingRules.partitionForKey("user-7", 8);
            assertEquals(p, OrderingRules.partitionForKey("user-7", 8));
            assertTrue(p >= 0 && p < 8);
        }
        assertEquals(
                OrderingRules.partitionForKey("user-7".getBytes(StandardCharsets.UTF_8), 64),
                OrderingRules.partitionForKey("user-7", 64));
    }

    @Test
    void partitionForKeyShouldTreatNullAsEmptyAndValidatePartitions() {
        assertEquals(OrderingRules.partitionForKey("", 16), OrderingRules.partitionForKey((String) null, 16));
        assertThrows(IllegalArgumentException.class, () -> OrderingRules.partitionForKey("k", 0));
        assertThrows(IllegalArgumentException.class, () -> OrderingRules.partitionForKey("k", -3));
    }

    @Test
    void compareVersionShouldTreatNullAsLowest() {
        assertEquals(0, OrderingRules.compareVersion(null, null));
        assertTrue(OrderingRules.compareVersion(null, 1L) < 0);
        assertTrue(OrderingRules.compareVersion(1L, null) > 0);
        assertEquals(0, OrderingRules.compareVersion(5L, 5L));
        assertTrue(OrderingRules.compareVersion(6L, 5L) > 0);
    }

    @Test
    void shouldProcessShouldRequireStrictlyNewerVersion() {
        assertTrue(OrderingRules.shouldProcess(null, 1L), "首次见到该 key 应处理");
        assertTrue(OrderingRules.shouldProcess(1L, 2L));
        assertFalse(OrderingRules.shouldProcess(2L, 2L), "重复投递丢弃");
        assertFalse(OrderingRules.shouldProcess(3L, 2L), "迟到旧版本丢弃");
        assertTrue(OrderingRules.shouldProcess(3L, null), "无版本信息保守放行");
        assertTrue(OrderingRules.shouldProcess(null, null));
    }

    @Test
    void isStaleShouldBeExactNegationOfShouldProcess() {
        assertTrue(OrderingRules.isStale(5L, 4L));
        assertTrue(OrderingRules.isStale(5L, 5L));
        assertFalse(OrderingRules.isStale(4L, 5L));
        assertFalse(OrderingRules.isStale(5L, null));
    }
}
