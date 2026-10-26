package com.acme.scaffold.common.tenant;

/**
 * 租户上下文：请求/线程级当前租户与作用域标记（ThreadLocal 载体）。
 *
 * <p><b>与 {@code CurrentPrincipal.tenantId} 的区别</b>：后者是登录主体携带的「身份租户」，
 * 由认证环节写入、不可变；本上下文是「当前执行作用域」的显式载体，可由调用方按场景覆盖：
 * <ul>
 *   <li>普通请求：由 {@code TenantInterceptor}（P3）从主体/请求头/Token Claim 解析后写入；</li>
 *   <li>平台管理员跨租户操作：显式 set 目标租户（平台管理员作用域下允许切换）；</li>
 *   <li>异步任务/系统作业/无主体场景：调用方显式 set，避免「未设置」被默认误判。</li>
 * </ul>
 *
 * <p><b>严格模式</b>：依赖该上下文的代码（jOOQ 租户注入、数据权限等）在「未设置」时由
 * {@link #requireTenantId()} 抛 {@link IllegalStateException}，强制调用方显式处理缺省，杜绝隐式越权。
 * {@link #clear()} 必须在请求/任务结束时调用，避免线程池复用导致作用域串台。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<TenantScope> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 写入当前线程的租户作用域（覆盖式）。 */
    public static void set(long tenantId, boolean platformAdmin) {
        HOLDER.set(new TenantScope(tenantId, platformAdmin));
    }

    /** 仅写入租户 ID（非平台管理员作用域）。 */
    public static void setTenantId(long tenantId) {
        HOLDER.set(new TenantScope(tenantId, false));
    }

    /** 读取当前作用域（未设置返回 null）。 */
    public static TenantScope current() {
        return HOLDER.get();
    }

    /**
     * 当前租户 ID（未设置抛 IllegalStateException，强制调用方显式处理缺省）。
     */
    public static long requireTenantId() {
        TenantScope s = HOLDER.get();
        if (s == null) {
            throw new IllegalStateException("未设置租户上下文（TenantContext），无法解析当前租户");
        }
        return s.tenantId();
    }

    /** 当前是否为平台管理员作用域。 */
    public static boolean isPlatformAdmin() {
        TenantScope s = HOLDER.get();
        return s != null && s.platformAdmin();
    }

    /** 是否已设置有效作用域。 */
    public static boolean isSet() {
        return HOLDER.get() != null;
    }

    /**
     * 在指定作用域下执行动作，结束后恢复调用前的作用域（支持嵌套）。
     * 调用前未设置时，结束后清除（不残留空作用域）。
     */
    public static void runWith(long tenantId, boolean platformAdmin, Runnable action) {
        runWith(new TenantScope(tenantId, platformAdmin), action);
    }

    /**
     * 在指定作用域下执行动作，结束后恢复调用前的作用域（支持嵌套）。
     */
    public static void runWith(TenantScope scope, Runnable action) {
        TenantScope prev = HOLDER.get();
        HOLDER.set(scope);
        try {
            action.run();
        } finally {
            if (prev == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(prev);
            }
        }
    }

    /** 清除当前线程的租户作用域（务必在请求/任务结束时调用）。 */
    public static void clear() {
        HOLDER.remove();
    }

    /** 不可变作用域值对象：租户 ID + 平台管理员标记。 */
    public record TenantScope(long tenantId, boolean platformAdmin) {
    }
}
