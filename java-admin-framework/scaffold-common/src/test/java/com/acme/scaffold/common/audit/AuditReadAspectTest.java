package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 读操作审计切面单测（M20 增强）：验证「默认关闭 / 采样控制 / 异常路径」三态。
 *
 * <p>采样率用 0.0 与 1.0 两端即可确定性判定（无需控制 Random 种子）：
 * 1.0 时 {@code random.nextDouble() < 1.0} 恒真，0.0 时恒假。</p>
 */
class AuditReadAspectTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityContextFacade securityContextFacade = Mockito.mock(SecurityContextFacade.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private final ClientIpResolver clientIpResolver = Mockito.mock(ClientIpResolver.class);

    private AuditProperties props;
    private AuditAspect aspect;

    @BeforeEach
    void reset() {
        Mockito.reset(auditLogService);
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());
        props = new AuditProperties();
        aspect = new AuditAspect(securityContextFacade, auditLogService, objectMapper, clientIpResolver, props);
    }

    @Test
    @DisplayName("读审计默认关闭：auditReads=false 时不落库")
    void disabledByDefault() throws Throwable {
        props.setAuditReads(false);
        aspect.aroundRead(joinPoint(new Object[]{"x"}, SampleService.class, "view"));
        verify(auditLogService, never()).saveAsync(any());
    }

    @Test
    @DisplayName("开启但采样率=0：不落库（与关闭等价）")
    void sampleRateZero() throws Throwable {
        props.setAuditReads(true);
        props.setReadSampleRate(0.0);
        aspect.aroundRead(joinPoint(new Object[]{"x"}, SampleService.class, "view"));
        verify(auditLogService, never()).saveAsync(any());
    }

    @Test
    @DisplayName("开启且采样率=1：全量落库，操作类型为 READ")
    void sampleRateOneAudits() throws Throwable {
        props.setAuditReads(true);
        props.setReadSampleRate(1.0);
        aspect.aroundRead(joinPoint(new Object[]{"x"}, SampleService.class, "view"));
        org.mockito.ArgumentCaptor<AuditRecord> captor = org.mockito.ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditLogService).saveAsync(captor.capture());
        AuditRecord record = captor.getValue();
        assertNotNull(record);
        assertEquals("READ", record.getOperationType());
        assertEquals("查看敏感详情", record.getOperationName());
    }

    @Test
    @DisplayName("读审计命中且目标抛异常：记录失败并原样抛出")
    void exceptionPath() throws Throwable {
        props.setAuditReads(true);
        props.setReadSampleRate(1.0);
        ProceedingJoinPoint pjp = joinPoint(new Object[]{"x"}, SampleService.class, "view");
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));
        try {
            aspect.aroundRead(pjp);
        } catch (IllegalStateException ignored) {
            // 预期原样抛出
        }
        org.mockito.ArgumentCaptor<AuditRecord> captor = org.mockito.ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditLogService).saveAsync(captor.capture());
        assertEquals("EXCEPTION", captor.getValue().getResultCode());
    }

    private ProceedingJoinPoint joinPoint(Object[] args, Class<?> target, String methodName) throws Throwable {
        Method method = target.getDeclaredMethod(methodName, String.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn("ok");
        when(pjp.proceed(any())).thenReturn("ok");
        return pjp;
    }

    /** 承载 {@link AuditRead} 注解的目标类型。 */
    static class SampleService {
        @AuditRead(module = "system", type = "READ", name = "查看敏感详情")
        public Object view(String id) {
            return "ok";
        }
    }
}
