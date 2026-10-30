package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.web.ClientIpResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 审计摘要 JSON 合法性单测。
 *
 * <p>锁定契约：{@code request_summary}/{@code result_summary} 为 JSON 列，
 * 摘要无论序列化失败还是超长截断，产出的值都必须能被 {@link ObjectMapper#readTree} 解析——
 * 否则 MySQL 直接抛 3140 Invalid JSON text，整条审计静默丢失（联机 IT 曾复现）。</p>
 */
class AuditAspectTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SecurityContextFacade securityContextFacade = Mockito.mock(SecurityContextFacade.class);

    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);

    private final ClientIpResolver clientIpResolver = Mockito.mock(ClientIpResolver.class);

    private final AuditAspect aspect =
            new AuditAspect(securityContextFacade, auditLogService, objectMapper, clientIpResolver);

    @BeforeEach
    void reset() {
        Mockito.reset(auditLogService);
        Mockito.when(securityContextFacade.getCurrentPrincipal()).thenReturn(java.util.Optional.empty());
    }

    @Test
    @DisplayName("序列化失败：摘要降级为合法 JSON 字符串标量（非裸类名）")
    void fallsBackToValidJsonScalarOnSerializationFailure() throws Throwable {
        Object unserializable = new Object() {
            public String getBoom() {
                throw new IllegalStateException("no serializer");
            }
        };

        AuditRecord record = captureRecord(new Object[]{unserializable});

        assertNotNull(record.getRequestSummary(), "摘要不应为 null");
        assertTrue(isValidJson(record.getRequestSummary()),
                "序列化失败的摘要必须是合法 JSON，实际值: " + record.getRequestSummary());
    }

    @Test
    @DisplayName("超长摘要：截断点落在转义中间时补齐结构，保留绝大部分原文且合法")
    void truncatedSummaryRemainsValidJson() throws Throwable {
        // 2000 字符边界处制造转义序列：a、\"、\\ 三字符循环，2000 = 3*666+2 恰好切断在引号上
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 2600) {
            sb.append('a').append('"').append('\\');
        }
        Object midEscapeText = sb.toString();

        AuditRecord record = captureRecord(new Object[]{midEscapeText});

        assertNotNull(record.getRequestSummary());
        assertTrue(record.getRequestSummary().length() <= 2000, "摘要不得超过 2000 字符");
        assertTrue(record.getRequestSummary().length() > 1900, "应尽量保留原文而非整体降级");
        assertTrue(isValidJson(record.getRequestSummary()),
                "截断后的摘要必须是合法 JSON，实际值: " + record.getRequestSummary());
    }

    @Test
    @DisplayName("超长摘要：数组包裹的字符串补齐闭合引号与右括号，内容基本保留且合法")
    void cleanCutPreservesFullTruncatedJson() throws Throwable {
        Object longText = "a".repeat(2600);

        AuditRecord record = captureRecord(new Object[]{longText});

        String summary = record.getRequestSummary();
        assertTrue(summary.length() <= 2000, "摘要不得超过 2000 字符");
        assertTrue(summary.length() > 1900, "应尽量保留原文而非整体降级");
        assertTrue(summary.startsWith("[\"") && summary.endsWith("\"]"),
                "补齐后应为闭合的数组字符串结构，实际值前缀: " + summary.substring(0, 8));
        assertTrue(isValidJson(summary), "截断后的摘要必须是合法 JSON");
    }

    @Test
    @DisplayName("正常参数：摘要为脱敏后的合法 JSON")
    void normalArgsProduceMaskedJson() throws Throwable {
        AuditRecord record = captureRecord(new Object[]{new LoginCmd("admin", "13800138000")});

        assertNotNull(record.getRequestSummary());
        assertTrue(isValidJson(record.getRequestSummary()));
        assertTrue(record.getRequestSummary().contains("138****8000"),
                "手机号应被脱敏为 138****8000，实际值: " + record.getRequestSummary());
    }

    @Test
    @DisplayName("recordParams=false：不记录参数摘要")
    void skipsParamSummaryWhenDisabled() throws Throwable {
        Object result = aspect.around(joinPoint(new Object[]{new LoginCmd("admin", "13800138000")},
                SampleService.class, "updatePassword"));

        assertNotNull(result);
        AuditRecord record = capture();
        assertNull(record.getRequestSummary(), "recordParams=false 时不得记录参数摘要");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private AuditRecord captureRecord(Object[] args) throws Throwable {
        Object result = aspect.around(joinPoint(args, SampleService.class, "login"));
        assertNotNull(result);
        return capture();
    }

    private AuditRecord capture() {
        org.mockito.ArgumentCaptor<AuditRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AuditRecord.class);
        Mockito.verify(auditLogService).saveAsync(captor.capture());
        return captor.getValue();
    }

    private ProceedingJoinPoint joinPoint(Object[] args, Class<?> target, String methodName) throws Throwable {
        Method method = target.getDeclaredMethod(methodName, LoginCmd.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn("ok");
        when(pjp.proceed(any())).thenReturn("ok");
        return pjp;
    }

    private boolean isValidJson(String candidate) {
        try {
            JsonNode node = objectMapper.readTree(candidate);
            return node != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 压测对象：带公开 getter 的简单命令对象。 */
    record LoginCmd(String username, String mobile) {
    }

    /** 承载 {@link AuditOperation} 注解的目标类型。 */
    static class SampleService {

        @AuditOperation(module = "system", type = "LOGIN", name = "登录", recordParams = true)
        public Object login(LoginCmd cmd) {
            return "ok";
        }

        @AuditOperation(module = "system", type = "UPDATE", name = "改密码", recordParams = false)
        public Object updatePassword(LoginCmd cmd) {
            return "ok";
        }
    }
}
