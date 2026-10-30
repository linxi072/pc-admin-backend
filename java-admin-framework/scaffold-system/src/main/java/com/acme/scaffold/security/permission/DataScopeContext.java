package com.acme.scaffold.security.permission;

/**
 * 数据权限上下文：由 {@link DataScopeAspect} 在查询方法执行前写入，方法结束后清理。
 *
 * <p>用 ThreadLocal 而非方法参数传递，是为了让「接入」只需要在 Service 方法上加一个
 * {@link DataScope} 注解——查询方法内部直接取当前范围拼条件即可，不必为传递范围
 * 改动每一层方法签名（这也是此前每个 Service 都要手工接线的原因）。
 *
 * <p>生命周期严格限定在切面内（try/finally 清理），不跨请求、不进线程池任务。
 */
public final class DataScopeContext {

    private static final ThreadLocal<Scope> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /** 一次查询的数据范围快照。 */
    public record Scope(Long userId, DataScopeResult result) {
    }

    public static void set(Scope scope) {
        HOLDER.set(scope);
    }

    /**
     * 当前上下文；无上下文（未标注 {@link DataScope} 的方法、或定时任务等非 Web 调用）返回 null，
     * 由调用方决定兜底策略，避免「静默当成不限」。
     */
    public static Scope current() {
        return HOLDER.get();
    }

    /** 清理上下文，必须在 finally 中调用，否则线程复用会串味（Tomcat 线程池）。 */
    public static void clear() {
        HOLDER.remove();
    }
}
