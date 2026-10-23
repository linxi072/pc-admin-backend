package com.acme.scaffold.system.notification.rules;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DeliveryRetryRules} 测试：可重试判定矩阵、线性退避计算、次数钳制。
 * 纯函数离线单测，无 Spring 上下文与时钟依赖。
 */
@DisplayName("投递重试规则：可重试判定、线性退避与次数钳制")
class DeliveryRetryRulesTest {

    @Test
    @DisplayName("BusinessException 不重试（确定性拒绝重试无益）")
    void businessExceptionNotRetryable() {
        assertFalse(DeliveryRetryRules.isRetryable(
                new BusinessException(CommonErrorCode.VALIDATION_ERROR, "渠道未启用")));
        assertFalse(DeliveryRetryRules.isRetryable(
                new BusinessException(CommonErrorCode.NOT_FOUND, "模板不存在")));
    }

    @Test
    @DisplayName("其余运行时异常可重试（瞬时故障：网关 IO / 传输失败 / 未知运行时错误）")
    void transientFailuresRetryable() {
        assertTrue(DeliveryRetryRules.isRetryable(new IllegalStateException("gateway down")));
        assertTrue(DeliveryRetryRules.isRetryable(new RuntimeException("connection reset")));
    }

    @Test
    @DisplayName("退避线性：第 n 次重试等 n × baseMs，总等待有硬上界")
    void backoffIsLinearAndBounded() {
        assertEquals(0L, DeliveryRetryRules.backoffMs(1, 0));
        assertEquals(200L, DeliveryRetryRules.backoffMs(1, 200));
        assertEquals(400L, DeliveryRetryRules.backoffMs(2, 200));
        assertEquals(600L, DeliveryRetryRules.backoffMs(3, 200));
        // 负 base 钳制为 0（配置防御）
        assertEquals(0L, DeliveryRetryRules.backoffMs(2, -100));
        // attempt < 1 按 1 处理
        assertEquals(200L, DeliveryRetryRules.backoffMs(0, 200));
    }

    @Test
    @DisplayName("次数钳制：<1 按 1（至少原样投递一次，不静默跳过）")
    void clampAttempts() {
        assertEquals(1, DeliveryRetryRules.clampAttempts(1));
        assertEquals(3, DeliveryRetryRules.clampAttempts(3));
        assertEquals(1, DeliveryRetryRules.clampAttempts(0));
        assertEquals(1, DeliveryRetryRules.clampAttempts(-5));
    }
}
