package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.web.ClientIpResolver;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/**
 * 操作审计切面：对标注 {@link AuditOperation} 的方法自动记录审计日志。
 * 参数与结果序列化后统一经 {@link SensitiveMasker#maskSummary(String)} 脱敏并截断至 2000 字符——
 * <b>不做字段级白名单</b>，因此涉及密钥/凭据的接口应显式设置 {@code recordParams = false}。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final SecurityContextFacade securityContextFacade;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Around("@annotation(com.acme.scaffold.common.audit.AuditOperation)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        AuditOperation annotation = AnnotationUtils.findAnnotation(
                ((MethodSignature) joinPoint.getSignature()).getMethod(), AuditOperation.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        String traceId = org.slf4j.MDC.get("traceId");
        String ip = resolveClientIp();
        String method = resolveMethod();
        String path = resolvePath();
        String paramSummary = annotation.recordParams() ? safeSummarize(joinPoint.getArgs()) : null;

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            String resultSummary = annotation.recordResult() ? safeSummarize(result) : null;
            AuditRecord record = build(annotation, ip, traceId, method, path, paramSummary, resultSummary, "0", true, duration);
            auditLogService.saveAsync(record);
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            AuditRecord record = build(annotation, ip, traceId, method, path, paramSummary, null, "EXCEPTION", false, duration);
            auditLogService.saveAsync(record);
            throw t;
        }
    }

    private AuditRecord build(AuditOperation a, String ip, String traceId, String method, String path,
                              String paramSummary, String resultSummary, String resultCode, boolean success, long duration) {
        AuditRecord record = AuditRecord.of(a.module(), a.type(), a.name())
                .request(method, path, paramSummary)
                .result(resultSummary, resultCode, success)
                .meta(ip, traceId, duration);
        securityContextFacade.getCurrentPrincipal()
                .ifPresent(p -> record.operator(p.userId(), p.displayName()));
        return record;
    }

    private String safeSummarize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            // 落库前统一脱敏：参数/结果可能携带密码、手机号、邮箱、身份证等敏感值
            // （典型如「重置用户密码」接口的新密码明文），否则持有审计敏感权限者即可原样读回
            String masked = SensitiveMasker.maskSummary(json);
            return masked.length() > 2000 ? masked.substring(0, 2000) : masked;
        } catch (Exception e) {
            return value.getClass().getSimpleName();
        }
    }

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        // 非 HTTP 上下文（定时任务等）返回 null，由审计记录按缺失处理
        return request == null ? null : clientIpResolver.resolve(request);
    }

    private String resolveMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod();
    }

    private String resolvePath() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getRequestURI();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
