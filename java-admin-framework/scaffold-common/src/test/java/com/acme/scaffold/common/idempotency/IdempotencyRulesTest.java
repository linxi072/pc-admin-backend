package com.acme.scaffold.common.idempotency;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdempotencyRules} 单元测试：幂等是否安全全靠这三条规则——
 * 键校验不严会撞键、指纹不严格会误重放、状态判定错会漏拦重复审批。
 */
class IdempotencyRulesTest {

    // ---------------- 键合法性 ----------------

    @Test
    void acceptsUrlSafeKeyWithinLength() {
        assertTrue(IdempotencyRules.isValidKey("9f8e7d6c5b4a3210"));
        assertTrue(IdempotencyRules.isValidKey("order_2026-001"));
        assertTrue(IdempotencyRules.isValidKey("a".repeat(IdempotencyRules.MAX_KEY_LENGTH)));
    }

    @Test
    void rejectsTooShortTooLongOrDirtyKey() {
        assertFalse(IdempotencyRules.isValidKey(null));
        assertFalse(IdempotencyRules.isValidKey("   "));
        assertFalse(IdempotencyRules.isValidKey("abc"));                                  // 过短
        assertFalse(IdempotencyRules.isValidKey("a".repeat(IdempotencyRules.MAX_KEY_LENGTH + 1)));
        assertFalse(IdempotencyRules.isValidKey("key with space!!"));                     // 含非法字符
        assertFalse(IdempotencyRules.isValidKey("<script>alert</script>"));
    }

    @Test
    void requireValidKeyThrows400WithoutEchoingKey() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> IdempotencyRules.requireValidKey("短"));
        assertEquals(IdempotencyErrorCode.KEY_INVALID, ex.getErrorCode());
        assertFalse(ex.getMessage().contains("短"), "错误消息不应回显原始 key");
    }

    // ---------------- 请求指纹 ----------------

    @Test
    void fingerprintIsDeterministic() {
        String a = IdempotencyRules.fingerprint("POST", "/api/x", "{\"amt\":1}");
        String b = IdempotencyRules.fingerprint("POST", "/api/x", "{\"amt\":1}");
        assertEquals(a, b);
        assertEquals(64, a.length());
    }

    @Test
    void fingerprintDiffersOnBodyMethodOrUri() {
        String base = IdempotencyRules.fingerprint("POST", "/api/x", "{\"amt\":1}");
        assertNotEquals(base, IdempotencyRules.fingerprint("POST", "/api/x", "{\"amt\":2}"));
        assertNotEquals(base, IdempotencyRules.fingerprint("PUT", "/api/x", "{\"amt\":1}"));
        assertNotEquals(base, IdempotencyRules.fingerprint("POST", "/api/y", "{\"amt\":1}"));
    }

    @Test
    void fingerprintHandlesNulls() {
        assertEquals(IdempotencyRules.fingerprint(null, null, null),
                IdempotencyRules.fingerprint("", "", ""));
    }

    // ---------------- 状态判定 ----------------

    @Test
    void noRecordProceeds() {
        assertEquals(IdempotencyAction.PROCEED, IdempotencyRules.decide(null, true));
    }

    @Test
    void successReplaysAndProcessingConflicts() {
        assertEquals(IdempotencyAction.REPLAY, IdempotencyRules.decide(IdempotencyStatus.SUCCESS, true));
        assertEquals(IdempotencyAction.CONFLICT, IdempotencyRules.decide(IdempotencyStatus.PROCESSING, true));
    }

    @Test
    void failedAllowsRetry() {
        assertEquals(IdempotencyAction.PROCEED, IdempotencyRules.decide(IdempotencyStatus.FAILED, true));
    }

    @Test
    void fingerprintMismatchAlwaysRejects() {
        // 无论当前处于哪个状态，key 相同但内容变了都必须拒绝，不能重放也不能放行
        assertEquals(IdempotencyAction.MISMATCH, IdempotencyRules.decide(IdempotencyStatus.SUCCESS, false));
        assertEquals(IdempotencyAction.MISMATCH, IdempotencyRules.decide(IdempotencyStatus.PROCESSING, false));
        assertEquals(IdempotencyAction.MISMATCH, IdempotencyRules.decide(IdempotencyStatus.FAILED, false));
    }

    // ---------------- 作用域 ----------------

    @Test
    void declaredScopeWinsOtherwiseSignature() {
        assertEquals("workflow:task:complete",
                IdempotencyRules.resolveScope("workflow:task:complete", "WorkflowService.complete(..)"));
        assertEquals("WorkflowService.complete(..)",
                IdempotencyRules.resolveScope("  ", "WorkflowService.complete(..)"));
        assertEquals("default", IdempotencyRules.resolveScope(null, null));
    }
}
