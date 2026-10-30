package com.acme.scaffold.common.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link TenantBypassAspect} 切面契约测试（沿用 AuditAspectTest 的直调 around 范式）。
 */
class TenantBypassAspectTest {

    private final TenantBypassAspect aspect = new TenantBypassAspect();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("@TenantIgnorable 方法执行期间置位旁路，结束后恢复")
    void setsBypassWithinAnnotatedMethod() throws Throwable {
        TenantContext.setTenantId(9);
        try {
            Object result = aspect.around(joinPoint(true));
            assertEquals("ok", result);
        } finally {
            TenantContext.clear();
        }
        // 方法结束后应已恢复非旁路
        TenantContext.setTenantId(9);
        try {
            assertFalse(TenantContext.isBypass(), "方法外不应处于旁路");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("未设置租户上下文时直接放行，不误置平台旁路")
    void proceedsWithoutContext() throws Throwable {
        TenantContext.clear();
        Object result = aspect.around(joinPoint(false));
        assertEquals("ok", result);
    }

    private ProceedingJoinPoint joinPoint(boolean expectBypassInside) throws Throwable {
        Method method = SampleService.class.getDeclaredMethod("crossTenantOp");
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.proceed()).thenAnswer(invocation -> {
            if (expectBypassInside) {
                assertTrue(TenantContext.isBypass(), "注解方法内应处于旁路");
            } else {
                assertFalse(TenantContext.isBypass(), "无上下文时不应误置旁路");
            }
            return "ok";
        });
        when(pjp.proceed(any())).thenReturn("ok");
        return pjp;
    }

    /** 承载 {@link TenantIgnorable} 注解的目标类型。 */
    static class SampleService {

        @TenantIgnorable
        public Object crossTenantOp() {
            return "ok";
        }
    }
}
