package com.acme.scaffold.common.tenant;

import java.util.List;

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
 *
 * <p><b>启动护栏</b>：当 {@code enforce=true} 时，应用启动期须通过 {@link #validateConfiguration()}
 * 校验配置自洽（默认租户合法、平台维表非空），否则 fail-fast 阻止「配置不全却开启强制隔离」上线。
 * 由 framework 模块的 {@code TenantEnforcementValidator}（{@code CommandLineRunner}）在启动时调用。</p>
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

    /** 配置快照是否已注入（非 null）。 */
    public static boolean isConfigured() {
        return properties != null;
    }

    /**
     * 校验隔离配置的自洽性（开启 enforce 前的启动护栏）。
     *
     * <p>仅当 {@code enforce=true} 时执行严格校验；{@code enforce=false} 时直接放行
     * （隔离实际不生效，无需校验，避免对既有单租户部署造成启动失败）。</p>
     *
     * <p>校验项：
     * <ol>
     *   <li>配置快照必须已注入（{@code configure} 已调用）；</li>
     *   <li>{@code defaultTenantId} 必须合法（非负长整型，不能为 null）；</li>
     *   <li>{@code platform-tables} 平台共享维表列表必须非空，否则平台维表会被错误地追加 tenant_id 过滤而不可见。</li>
     * </ol>
     *
     * @throws IllegalStateException 当 {@code enforce=true} 但配置不自洽，提示含可操作的修正建议
     */
    public static void validateConfiguration() {
        if (!isEnabled()) {
            return;
        }
        TenantProperties props = properties;
        if (props == null) {
            throw new IllegalStateException(
                    "租户隔离已开启(enforce=true)但配置未注入：请在应用启动时调用 TenantEnforcement.configure("
                            + "tenantProperties) 注入 app.tenant 配置");
        }
        if (!TenantRules.isValidTenantId(props.getDefaultTenantId())) {
            throw new IllegalStateException(
                    "租户隔离已开启(enforce=true)但 defaultTenantId 非法（" + props.getDefaultTenantId()
                            + "）：必须为非负长整型，建议设为平台租户 " + TenantRules.PLATFORM_TENANT_ID);
        }
        List<String> platformTables = props.getPlatformTables();
        if (platformTables == null || platformTables.isEmpty()) {
            throw new IllegalStateException(
                    "租户隔离已开启(enforce=true)但 platform-tables 为空：平台共享维表（sys_tenant/sys_menu/"
                            + "sys_dict_type/sys_dict_data/sys_config/sys_api_resource）必须纳入豁免列表，"
                            + "否则将错误地对平台维表追加 tenant_id 过滤，导致平台数据不可见");
        }
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
