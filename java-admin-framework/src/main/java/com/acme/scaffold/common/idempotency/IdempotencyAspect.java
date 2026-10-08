package com.acme.scaffold.common.idempotency;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 幂等切面：对标注 {@link Idempotent} 的方法按 {@code Idempotency-Key} 去重。
 *
 * <p><b>为什么必须排在事务切面外层</b>（{@code order = LOWEST_PRECEDENCE - 1000}）：
 * 只有包住 {@code @Transactional}，{@code proceed()} 返回时才意味着业务事务<b>已提交</b>，
 * 此时写 SUCCESS 才安全；否则会出现「业务回滚但幂等记录显示成功」，
 * 客户端重试被重放成一个从未真正生效的结果。
 *
 * <p><b>错误处理立场</b>：
 * <ul>
 *   <li>业务异常（{@link BusinessException}）→ 记 FAILED 并保留错误码，允许修正后重试；</li>
 *   <li>其它异常（系统错误、连接中断）→ 删除记录，不污染客户端重试；</li>
 *   <li>幂等基础设施自身异常 → 记日志并放行，绝不因为「去重功能故障」阻断业务。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class IdempotencyAspect implements Ordered {

    /** 幂等键请求头名。 */
    public static final String HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final SecurityContextFacade securityContextFacade;

    public IdempotencyAspect(IdempotencyService idempotencyService, ObjectMapper objectMapper,
                             SecurityContextFacade securityContextFacade) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.securityContextFacade = securityContextFacade;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = currentKey();
        if (key == null || key.isBlank()) {
            if (idempotent.requireKey()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, idempotent.message());
            }
            // 未强制且无 key：完全不介入，保持方法原语义（内部复用场景）
            return joinPoint.proceed();
        }

        // ---- 阶段一：登记。失败只影响「去重」本身，降级为不去重，绝不阻断业务 ----
        IdempotencyService.AcquireResult acquired;
        String scope;
        try {
            IdempotencyRules.requireValidKey(key);
            scope = IdempotencyRules.resolveScope(idempotent.scope(),
                    joinPoint.getSignature().toShortString());
            String fingerprint = IdempotencyRules.fingerprint(currentMethod(), currentUri(),
                    bodyFingerprint(joinPoint));
            acquired = idempotencyService.acquire(key.trim(), scope, currentUserId(), fingerprint,
                    idempotent.ttlSeconds());
        } catch (BusinessException e) {
            // 键非法(400) / 处理中(409) / 指纹不一致(422)：原样上抛，交由全局异常映射
            throw e;
        } catch (Exception e) {
            log.error("幂等登记失败，降级为不去重", e);
            return joinPoint.proceed();
        }

        // ---- 阶段二：按命中动作分流 ----
        switch (acquired.action()) {
            case REPLAY -> {
                log.info("幂等重放 key={} scope={}", key, scope);
                return replay(acquired.responseSnapshot());
            }
            case CONFLICT -> throw new BusinessException(IdempotencyErrorCode.IN_PROGRESS);
            case MISMATCH -> throw new BusinessException(IdempotencyErrorCode.FINGERPRINT_MISMATCH);
            case PROCEED -> {
                // 放行，落到下面执行真实业务逻辑
            }
        }

        // ---- 阶段三：执行业务。回写失败只记日志，不影响业务结果 ----
        try {
            Object result = joinPoint.proceed();
            runQuietly(() -> idempotencyService.markSuccess(key.trim(), scope, result), "标记幂等成功");
            return result;
        } catch (BusinessException e) {
            runQuietly(() -> idempotencyService.markFailed(key.trim(), scope,
                    e.getErrorCode().code(), e.getMessage()), "标记幂等失败");
            throw e;
        } catch (Throwable t) {
            runQuietly(() -> idempotencyService.release(key.trim(), scope), "释放幂等记录");
            throw t;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1000;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 幂等回写失败只记日志：业务已经成功了，不能因为记账失败把成功变失败。 */
    private void runQuietly(Runnable task, String desc) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("{}失败", desc, e);
        }
    }

    private Object replay(String snapshot) {
        if (snapshot == null) {
            // 快照缺失（超长或序列化失败）：拒绝重放而不是重跑，宁可让客户端显式处理
            throw new BusinessException(IdempotencyErrorCode.DUPLICATE);
        }
        try {
            return objectMapper.readValue(snapshot, Object.class);
        } catch (Exception e) {
            log.warn("幂等快照反序列化失败", e);
            throw new BusinessException(IdempotencyErrorCode.DUPLICATE);
        }
    }

    private String currentKey() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getHeader(HEADER);
        }
        return null;
    }

    private String currentMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? "" : request.getMethod();
    }

    private String currentUri() {
        HttpServletRequest request = currentRequest();
        return request == null ? "" : request.getRequestURI();
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    /** 请求体指纹输入：参数序列化失败时退化为参数个数，保证不因序列化问题阻断调用。 */
    private String bodyFingerprint(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("幂等请求体序列化失败，退化为参数个数指纹", e);
            return "argc=" + args.length;
        }
    }

    private Long currentUserId() {
        return securityContextFacade.getCurrentPrincipal().map(CurrentPrincipal::userId).orElse(null);
    }
}
