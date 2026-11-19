package com.acme.scaffold.common.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应体 Result 单测（V1.1.0 测试覆盖重建立项：scaffold-common 批次）。
 */
class ResultTest {

    @Test
    void successShouldCarryDataAndSuccessCode() {
        Result<String> r = Result.success("hello");
        assertEquals(CommonConstants.SUCCESS_CODE, r.code());
        assertEquals("hello", r.data());
        assertEquals("OK", r.message());
        assertTrue(r.isSuccess());
        assertNotNull(r.timestamp());
    }

    @Test
    void successWithoutDataShouldUseNullData() {
        Result<Void> r = Result.success();
        assertNull(r.data());
        assertTrue(r.isSuccess());
    }

    @Test
    void errorShouldCarryCodeAndMessageWithoutData() {
        Result<String> r = Result.error("SYSTEM_USER_003", "用户不存在");
        assertFalse(r.isSuccess());
        assertEquals("SYSTEM_USER_003", r.code());
        assertEquals("用户不存在", r.message());
        assertNull(r.data());
    }

    @Test
    void errorWithDataShouldKeepPayload() {
        Result<String> r = Result.error("AUTH_001", "校验失败", "detail");
        assertEquals("detail", r.data());
    }

    @Test
    void traceIdShouldFallbackToDashWhenMdcEmpty() {
        org.slf4j.MDC.remove("traceId");
        Result<String> r = Result.success("x");
        assertEquals("-", r.traceId());
    }

    @Test
    void traceIdShouldFollowMdc() {
        org.slf4j.MDC.put("traceId", "t-123");
        try {
            Result<String> r = Result.success("x");
            assertEquals("t-123", r.traceId());
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }
}
