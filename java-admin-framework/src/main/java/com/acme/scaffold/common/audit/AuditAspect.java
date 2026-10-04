package com.acme.scaffold.common.audit;

import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
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

import java.time.Instant;

/**
 * 操作审计切面：对标注 {@link AuditOperation} 的方法自动记录审计日志。
 * 仅记录白名单字段，参数与结果均脱敏后截断。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final SecurityContextFacade securityContextFacade;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

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
            return json.length() > 2000 ? json.substring(0, 2000) : json;
        } catch (Exception e) {
            return value.getClass().getSimpleName();
        }
    }

    private String resolveClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
