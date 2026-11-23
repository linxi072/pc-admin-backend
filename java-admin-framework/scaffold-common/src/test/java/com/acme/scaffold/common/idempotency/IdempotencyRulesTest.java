package com.acme.scaffold.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 幂等纯规则单测（V1.1.0 第三轮优化）：锁定键校验、作用域解析、指纹与状态机动作判定，
 * 防止「改了金额再重发被静默重放」或「正常重试被误拦」等幂等风险回归。
 */
class IdempotencyRulesTest {

    @Test
    void validKeyShouldAcceptBoundaryLengthsAndAllowedChars() {
        assertEquals(true, IdempotencyRules.isValidKey("12345678")); // MIN 边界
        assertTrue(IdempotencyRules.isValidKey("abc_DEF-123"));
        assertTrue(IdempotencyRules.isValidKey("a".repeat(IdempotencyRules.MAX_KEY_LENGTH))); // MAX 边界
        assertTrue(IdempotencyRules.isValidKey("  12345678  "), "首尾空白应被 trim 后校验");
    }

    @Test
    void invalidKeyShouldBeRejected() {
        assertFalse(IdempotencyRules.isValidKey(null));
        assertFalse(IdempotencyRules.isValidKey(""));
        assertFalse(IdempotencyRules.isValidKey("1234567")); // 短于 MIN
        assertFalse(IdempotencyRules.isValidKey("a".repeat(IdempotencyRules.MAX_KEY_LENGTH + 1)));
        assertFalse(IdempotencyRules.isValidKey("has space"));
        assertFalse(IdempotencyRules.isValidKey("中文键名abc"));
        assertFalse(IdempotencyRules.isValidKey("a.b:c"), "仅允许 URL 安全字符");
    }

    @Test
    void requireValidKeyShouldThrowBusinessException() {
        assertThrows(com.acme.scaffold.common.exception.BusinessException.class,
                () -> IdempotencyRules.requireValidKey("bad!"));
        IdempotencyRules.requireValidKey("valid-key-123"); // 合法不抛
    }

    @Test
    void scopeShouldPreferDeclaredThenSignatureThenDefault() {
        assertEquals("declared", IdempotencyRules.resolveScope("  declared  ", "signature"));
        assertEquals("signature", IdempotencyRules.resolveScope("  ", " signature "));
        assertEquals("default", IdempotencyRules.resolveScope(null, "  "));
    }

    @Test
    void fingerprintShouldBeStableAndSensitiveToAllParts() {
        String base = IdempotencyRules.fingerprint("POST", "/api/order", "{\"amount\":100}");
        assertEquals(base, IdempotencyRules.fingerprint("POST", "/api/order", "{\"amount\":100}"), "相同输入指纹一致");
        assertEquals(64, base.length(), "SHA-256 十六进制长度为 64");
        assertTrue(base.matches("[0-9a-f]{64}"));
        assertNotEquals(base, IdempotencyRules.fingerprint("PUT", "/api/order", "{\"amount\":100}"), "方法不同");
        assertNotEquals(base, IdempotencyRules.fingerprint("POST", "/api/other", "{\"amount\":100}"), "路径不同");
        assertNotEquals(base, IdempotencyRules.fingerprint("POST", "/api/order", "{\"amount\":200}"), "请求体不同 -> 必须识别为不同请求");
        // 说明：分隔符 SEP 只用于区分 method/uri/body 三段；HTTP method、URI 与 JSON body
        // 按协议不可能包含 U+001F 控制字符，故不存在“输入自带分隔符”导致的碰撞场景。
        // 若未来改指纹算法（如逐段哈希），会导致历史幂等记录指纹失配，属行为不兼容变更，需单独评估。
    }

    @Test
    void decideShouldFollowStateMachine() {
        assertEquals(IdempotencyAction.PROCEED,
                IdempotencyRules.decide(null, true), "无记录 -> 放行");
        assertEquals(IdempotencyAction.MISMATCH,
                IdempotencyRules.decide(IdempotencyStatus.SUCCESS, false), "指纹不一致 -> MISMATCH");
        assertEquals(IdempotencyAction.REPLAY,
                IdempotencyRules.decide(IdempotencyStatus.SUCCESS, true), "成功且指纹一致 -> REPLAY");
        assertEquals(IdempotencyAction.CONFLICT,
                IdempotencyRules.decide(IdempotencyStatus.PROCESSING, true), "处理中 -> CONFLICT(409)");
        assertEquals(IdempotencyAction.PROCEED,
                IdempotencyRules.decide(IdempotencyStatus.FAILED, true), "失败 -> 允许重试");
    }

    @Test
    void businessFailureClassificationShouldOnlyMatchBusinessException() {
        assertTrue(IdempotencyRules.isBusinessFailure(
                new com.acme.scaffold.common.exception.BusinessException(
                        com.acme.scaffold.common.error.CommonErrorCode.VALIDATION_ERROR, "test")));
        assertFalse(IdempotencyRules.isBusinessFailure(new IllegalStateException("system error")));
        assertFalse(IdempotencyRules.isBusinessFailure(new RuntimeException("unknown")));
    }
}
