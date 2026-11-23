package com.acme.scaffold.system.notification.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * 投递重试规则单测（代码审查专项 III）：退避线性语义、次数钳制与溢出防护。
 */
class DeliveryRetryRulesTest {

    @Test
    void retryableShouldExcludeBusinessException() {
        assertFalse(DeliveryRetryRules.isRetryable(
                BusinessException.badRequest("deterministic")));
        assertTrue(DeliveryRetryRules.isRetryable(new RuntimeException("transient")));
    }

    @Test
    void backoffShouldBeLinear() {
        assertEquals(1000L, DeliveryRetryRules.backoffMs(1, 1000));
        assertEquals(2000L, DeliveryRetryRules.backoffMs(2, 1000));
        assertEquals(3000L, DeliveryRetryRules.backoffMs(3, 1000));
    }

    @Test
    void backoffShouldClampNonPositiveInputs() {
        assertEquals(0L, DeliveryRetryRules.backoffMs(3, -5));
        assertEquals(1000L, DeliveryRetryRules.backoffMs(-2, 1000), "attempt<1 归一为 1");
    }

    @Test
    void backoffShouldNotOverflowToNegative() {
        // 回归缺陷：base*attempt 溢出为负会被 awaitBackoff 视作 <=0 跳过退避（瞬时重试放大压力）。
        long huge = Long.MAX_VALUE / 2;
        long result = DeliveryRetryRules.backoffMs(10, huge);
        assertTrue(result > 0, "退避时长不得为负");
        assertEquals(Long.MAX_VALUE, result, "溢出时钳到 Long.MAX_VALUE");
    }

    @Test
    void attemptsShouldBeClamped() {
        assertEquals(1, DeliveryRetryRules.clampAttempts(0));
        assertEquals(3, DeliveryRetryRules.clampAttempts(3));
        assertEquals(DeliveryRetryRules.MAX_ATTEMPTS_CAP,
                DeliveryRetryRules.clampAttempts(Integer.MAX_VALUE));
    }
}
