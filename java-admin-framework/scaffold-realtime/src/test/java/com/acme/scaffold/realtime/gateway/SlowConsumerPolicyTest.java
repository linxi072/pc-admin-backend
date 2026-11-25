package com.acme.scaffold.realtime.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.realtime.gateway.SlowConsumerPolicy.SlowLevel;
import org.junit.jupiter.api.Test;

/** 慢消费者三级处置单测：队列阈值分档与节点熔断边界。 */
class SlowConsumerPolicyTest {

    @Test
    void designDefaultShouldMatchSpec() {
        SlowConsumerPolicy p = SlowConsumerPolicy.designDefault();
        assertEquals(1000, p.queueLimit());
        assertEquals(0.70, p.nodeHighWater());
    }

    @Test
    void constructorShouldRejectInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new SlowConsumerPolicy(0, 0.7));
        assertThrows(IllegalArgumentException.class, () -> new SlowConsumerPolicy(1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlowConsumerPolicy(1000, 1.01));
    }

    @Test
    void classifyShouldStartOnlyAboveQueueLimit() {
        SlowConsumerPolicy p = SlowConsumerPolicy.designDefault();
        assertEquals(SlowLevel.NONE, p.classify(1000, true));
        assertEquals(SlowLevel.NONE, p.classify(999, false));
        assertEquals(SlowLevel.NONE, p.classify(0, false));
    }

    @Test
    void realtimeMessagesShouldDropOldestWhileOthersClose() {
        SlowConsumerPolicy p = SlowConsumerPolicy.designDefault();
        assertEquals(SlowLevel.L1_DROP_OLDEST, p.classify(1001, true));
        assertEquals(SlowLevel.L2_CLOSE_4029, p.classify(1001, false));
        assertEquals(SlowLevel.L2_CLOSE_4029, p.classify(5000, false));
    }

    @Test
    void circuitBreakShouldBeStrictlyAboveHighWater() {
        SlowConsumerPolicy p = SlowConsumerPolicy.designDefault();
        assertFalse(p.shouldCircuitBreak(0.70));
        assertTrue(p.shouldCircuitBreak(0.71));
        assertTrue(p.shouldCircuitBreak(1.0));
    }
}
