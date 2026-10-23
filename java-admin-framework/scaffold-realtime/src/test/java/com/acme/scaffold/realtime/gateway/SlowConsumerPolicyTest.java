package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SlowConsumerPolicy} 三级处置单测。 */
@DisplayName("SlowConsumerPolicy 慢消费者分级")
class SlowConsumerPolicyTest {

    private final SlowConsumerPolicy policy = SlowConsumerPolicy.designDefault();

    @Test
    @DisplayName("队列未超阈值：NONE")
    void withinLimit_none() {
        assertEquals(SlowConsumerPolicy.SlowLevel.NONE, policy.classify(500, true));
        assertEquals(SlowConsumerPolicy.SlowLevel.NONE, policy.classify(1000, false));
    }

    @Test
    @DisplayName("队列超阈值 + 实时类：L1 丢最旧")
    void overLimit_realtime_L1() {
        assertEquals(SlowConsumerPolicy.SlowLevel.L1_DROP_OLDEST, policy.classify(1001, true));
    }

    @Test
    @DisplayName("队列超阈值 + 关键类：L2 断链补洞")
    void overLimit_critical_L2() {
        assertEquals(SlowConsumerPolicy.SlowLevel.L2_CLOSE_4029, policy.classify(1001, false));
    }

    @Test
    @DisplayName("节点级熔断：水位 > 70% 触发")
    void circuitBreak_threshold() {
        assertTrue(policy.shouldCircuitBreak(0.71));
        assertFalse(policy.shouldCircuitBreak(0.70));
    }
}
