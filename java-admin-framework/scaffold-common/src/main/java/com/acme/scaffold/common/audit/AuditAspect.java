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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;


/**
 * 操作审计切面：对标注 {@link AuditOperation} 的方法自动记录审计日志。
 * 参数与结果序列化后统一经 {@link SensitiveMasker#maskSummary(String)} 脱敏并截断至 2000 字符——
 * <b>不做字段级白名单</b>，因此涉及密钥/凭据的接口应显式设置 {@code recordParams = false}。
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final SecurityContextFacade securityContextFacade;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final AuditProperties auditProperties;
    private final Random random = new Random();

    /** 保留 4 参构造器以兼容既有测试；读审计开关默认关闭。 */
    public AuditAspect(SecurityContextFacade securityContextFacade, AuditLogService auditLogService,
                       ObjectMapper objectMapper, ClientIpResolver clientIpResolver) {
        this(securityContextFacade, auditLogService, objectMapper, clientIpResolver, new AuditProperties());
    }

    public AuditAspect(SecurityContextFacade securityContextFacade, AuditLogService auditLogService,
                       ObjectMapper objectMapper, ClientIpResolver clientIpResolver,
                       AuditProperties auditProperties) {
        this.securityContextFacade = securityContextFacade;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.auditProperties = auditProperties;
    }

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

    @Around("@annotation(com.acme.scaffold.common.audit.AuditRead)")
    public Object aroundRead(ProceedingJoinPoint joinPoint) throws Throwable {
        // 读审计默认关闭：开关未开或采样未命中都不落库，仅透传原结果
        if (!auditProperties.isAuditReads()) {
            return joinPoint.proceed();
        }
        AuditRead annotation = AnnotationUtils.findAnnotation(
                ((MethodSignature) joinPoint.getSignature()).getMethod(), AuditRead.class);
        if (annotation == null) {
            return joinPoint.proceed();
        }
        // 采样：readSampleRate ∈ [0,1]，1=全量，0=不采（与开关关闭等价）
        double rate = auditProperties.getReadSampleRate();
        if (rate <= 0.0 || random.nextDouble() >= rate) {
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
            AuditRecord record = build(annotation.module(), annotation.type(), annotation.name(),
                    ip, traceId, method, path, paramSummary, resultSummary, "0", true, duration);
            auditLogService.saveAsync(record);
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - start;
            AuditRecord record = build(annotation.module(), annotation.type(), annotation.name(),
                    ip, traceId, method, path, paramSummary, null, "EXCEPTION", false, duration);
            auditLogService.saveAsync(record);
            throw t;
        }
    }

    private AuditRecord build(AuditOperation a, String ip, String traceId, String method, String path,
                              String paramSummary, String resultSummary, String resultCode, boolean success, long duration) {
        return build(a.module(), a.type(), a.name(), ip, traceId, method, path,
                paramSummary, resultSummary, resultCode, success, duration);
    }

    private AuditRecord build(String module, String type, String name, String ip, String traceId, String method,
                              String path, String paramSummary, String resultSummary, String resultCode,
                              boolean success, long duration) {
        AuditRecord record = AuditRecord.of(module, type, name)
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
            return truncateJson(masked);
        } catch (Exception e) {
            // 序列化失败时必须仍产出「合法 JSON 标量」：request_summary 为 JSON 列，
            // 裸类名（如 Object[]）会触发 MySQL 3140 Invalid JSON text 使整条审计静默丢失
            log.warn("审计摘要序列化失败，降级为类型名: {}", value.getClass().getName());
            return quoteJson(value.getClass().getSimpleName());
        }
    }

    /** 截断至 2000 字符且保证截断结果仍是合法 JSON（截断点可能落在转义序列中间）。 */
    private String truncateJson(String json) {
        if (json.length() <= 2000) {
            return json;
        }
        String truncated = json.substring(0, 2000);
        if (isValidJson(truncated)) {
            return truncated;
        }
        // 截断点几乎总落在字符串或容器内部：扫描未闭合结构并补齐，尽量保留原文
        // （审计的价值恰恰在超长摘要里，整体降级为占位符等于丢失证据）
        String repaired = repairTruncatedJson(truncated);
        if (repaired != null && isValidJson(repaired)) {
            return repaired;
        }
        return quoteJson("[truncated]");
    }

    /**
     * 补齐被截断 JSON 的闭合结构：按扫描栈补回未闭合的字符串引号（含悬空转义）与容器括号。
     *
     * <p>截断点落在裸字面量（数字/布尔）中间时无法安全补齐，返回 null 由调用方降级。</p>
     *
     * @param truncated 已截断的 JSON 文本（长度须 ≤ 2000）
     * @return 补齐后的合法 JSON 文本；无法补齐时返回 null
     */
    private String repairTruncatedJson(String truncated) {
        // 预留补齐空间：悬空转义 + 闭合引号 + 多层容器括号（预留 16 字符，更深嵌套降级处理）
        int budget = 2000 - 16;
        if (truncated.length() > budget) {
            truncated = truncated.substring(0, budget);
        }
        boolean inString = false;
        boolean escape = false;
        boolean inLiteral = false;
        Deque<Character> closers = new ArrayDeque<>();
        for (int i = 0; i < truncated.length(); i++) {
            char c = truncated.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                inLiteral = false;
            } else if (c == '{' || c == '[') {
                closers.push(c == '{' ? '}' : ']');
                inLiteral = false;
            } else if (c == '}' || c == ']') {
                if (!closers.isEmpty() && closers.peek() == c) {
                    closers.pop();
                }
                inLiteral = false;
            } else if (c == ',' || c == ':') {
                inLiteral = false;
            } else if (!Character.isWhitespace(c)) {
                if (c == '-' || c == '+' || c == '.' || Character.isLetterOrDigit(c)) {
                    inLiteral = true;
                } else {
                    inLiteral = false;
                }
            }
        }
        if (!inString && inLiteral) {
            // 裸字面量被拦腰截断（如 123→12、true→tr）：补齐任何括号都救不活，交给降级路径
            return null;
        }
        StringBuilder suffix = new StringBuilder();
        if (escape) {
            // 悬空反斜杠：先自转义再补引号，否则补的引号会被当成转义字符
            suffix.append('\\');
        }
        if (inString) {
            suffix.append('"');
        }
        while (!closers.isEmpty()) {
            suffix.append(closers.pop());
        }
        return truncated + suffix;
    }

    private boolean isValidJson(String candidate) {
        try {
            objectMapper.readTree(candidate);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 将任意文本包成合法 JSON 字符串标量（双引号包裹，供 JSON 列安全落库）。 */
    private String quoteJson(String text) {
        try {
            return objectMapper.writeValueAsString(text);
        } catch (Exception e) {
            return "\"[unserializable]\"";
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
