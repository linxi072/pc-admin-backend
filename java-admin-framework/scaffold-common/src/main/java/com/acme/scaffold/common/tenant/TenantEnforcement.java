package com.acme.scaffold.common.tenant;

/**
 * 租户隔离强制策略（M19 P3）：集中裁决「某次数据访问是否应追加 tenant_id 约束」。
 *
 * <p>本类持有 {@link TenantProperties} 的静态快照（由 {@link #configure} 在应用启动时注入），
 * 供 {@code JooqWriters}（动态 DSL 唯一的 CRUD 入口）在构建每条 SQL 时统一调用，
 * 避免把隔离逻辑散落到各 Service。采用静态快照而非 Spring 注入，是因为
 * {@code JooqWriters} 是静态工具类，且所有查询最终都汇聚于此，单点裁决即可覆盖读/改/删/写。</p>
 *
 * <p><b>生效条件（全部满足才对给定表强制隔离）</b>：
 * <ol>
 *   <li>{@code app.tenant.enforce=true}（默认 false，需显式开启，避免对既有单租户数据与测试回归）；</li>
 *   <li>当前线程已设置租户作用域（{@link TenantContext#isSet()}）；</li>
 *   <li>未处于 {@code @TenantIgnorable} 跨租户旁路（{@link TenantContext#isBypass()}）；</li>
 *   <li>目标表非「平台共享维表」（{@code app.tenant.platform-tables}）。</li>
 * </ol>
 */
public final class TenantEnforcement {

    private static volatile TenantProperties properties;

    private TenantEnforcement() {
    }

    /** 应用启动时注入配置快照（幂等，最后一次调用生效）。 */
    public static void configure(TenantProperties props) {
        properties = props;
    }

    /** 强制隔离总开关是否开启。 */
    public static boolean isEnabled() {
        return properties != null && properties.isEnforce();
    }

    /**
     * 是否对给定表名施加 tenant_id 隔离。
     *
     * @param tableName 目标表名（小写 snake_case），可为 null（null 视为不隔离）
     */
    public static boolean shouldEnforce(String tableName) {
        if (!isEnabled()) {
            return false;
        }
        TenantContext.TenantScope scope = TenantContext.current();
        if (scope == null) {
            // 非请求线程（定时任务/内部无主体调用）未解析租户，不强制，避免打断；作业侧应显式 set 作用域
            return false;
        }
        if (scope.bypass()) {
            return false;
        }
        if (tableName != null && properties.getPlatformTables().contains(tableName)) {
            return false;
        }
        return true;
    }

    /** 当前生效租户 ID（调用方需先经 {@link #shouldEnforce} 确认上下文已设置）。 */
    public static long currentTenantId() {
        return TenantContext.requireTenantId();
    }
}
