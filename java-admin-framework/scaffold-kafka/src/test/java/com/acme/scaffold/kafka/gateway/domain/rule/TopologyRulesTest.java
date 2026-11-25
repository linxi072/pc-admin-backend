package com.acme.scaffold.kafka.gateway.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 拓扑规则单测：acks 白名单、分区推导公式与单分区热点判定边界。 */
class TopologyRulesTest {

    @Test
    void acksShouldAcceptOnlyKafkaDocumentedValues() {
        assertTrue(TopologyRules.isValidAcks("0"));
        assertTrue(TopologyRules.isValidAcks("1"));
        assertTrue(TopologyRules.isValidAcks("all"));
        assertFalse(TopologyRules.isValidAcks("ALL"));
        assertFalse(TopologyRules.isValidAcks("2"));
        assertFalse(TopologyRules.isValidAcks(null));
    }

    @Test
    void suggestPartitionsShouldFollowDesignFormula() {
        assertEquals(144, TopologyRules.suggestPartitions(1_200_000L, 30_000, 96));
    }

    @Test
    void suggestPartitionsShouldRoundUpThroughputAndParallelism() {
        assertEquals(3, TopologyRules.suggestPartitions(101L, 50, 1), "101/50 向上取整 3 压过并行度 2");
        assertEquals(2, TopologyRules.suggestPartitions(100L, 50, 1));
        assertEquals(2, TopologyRules.suggestPartitions(0L, 50, 1), "吞吐为 0 仍按并行度给至少 2 分区");
    }

    @Test
    void suggestPartitionsShouldRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> TopologyRules.suggestPartitions(-1L, 30_000, 8));
        assertThrows(IllegalArgumentException.class, () -> TopologyRules.suggestPartitions(100L, 0, 8));
        assertThrows(IllegalArgumentException.class, () -> TopologyRules.suggestPartitions(100L, -1, 8));
        assertThrows(IllegalArgumentException.class, () -> TopologyRules.suggestPartitions(100L, 30_000, 0));
    }

    @Test
    void hotPartitionShouldUseStrictlyGreaterThanTwiceAverage() {
        assertTrue(TopologyRules.isHotPartition(0.7, 3), "0.7 > 2/3 构成热点");
        assertFalse(TopologyRules.isHotPartition(2.0 / 3.0, 3), "恰好 2× 均值不算热点");
        assertFalse(TopologyRules.isHotPartition(0.1, 4));
        assertThrows(IllegalArgumentException.class, () -> TopologyRules.isHotPartition(0.5, 0));
    }
}
