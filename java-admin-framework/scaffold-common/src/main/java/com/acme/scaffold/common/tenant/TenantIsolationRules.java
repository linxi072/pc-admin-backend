package com.acme.scaffold.common.tenant;

/**
 * 租户隔离态势规则（M28 纯函数）：组装只读诊断视图 {@link TenantIsolationStatusView}.
 *
 * <p>聚合三个来源，不写库、不触发任何隔离逻辑、不新增依赖：
 * <ol>
 *   <li>配置态：注入的 {@link TenantProperties}（隔离体系开关、默认租户、平台维表、解析源）；</li>
 *   <li>运行态：{@link TenantEnforcement} 静态快照（强制隔离是否生效、配置是否已注入）；</li>
 *   <li>当前执行作用域：{@link TenantContext}（当前请求线程的租户/平台管理员/旁路标记）；</li>
 *   <li>配置校验：调用 {@link TenantEnforcement#validateConfiguration()} 包裹 try/catch，
 *       得到 OK / INVALID（开启且配置非法）/ DISABLED（未开启，不校验）。</li>
 * </ol>
 *
 * <p>本类与既有隔离核心（TenantEnforcement / TenantContext / TenantRules）保持同一口径，
 * 仅做「只读聚合」，不复制任何裁决逻辑，避免双份事实来源。</p>
 */
public final class TenantIsolationRules {

    private TenantIsolationRules() {
    }

    /** 校验状态：配置自洽且强制隔离已生效。 */
    public static final String VALIDATION_OK = "OK";

    /** 校验状态：强制隔离已开启但配置非法（validateConfiguration 抛 IllegalStateException）。 */
    public static final String VALIDATION_INVALID = "INVALID";

    /** 校验状态：强制隔离未开启（enforce=false），配置不参与运行期强制，无需校验。 */
    public static final String VALIDATION_DISABLED = "DISABLED";

    /**
     * 组装租户隔离态势快照。
     *
     * @param props 注入的租户配置（来自 Spring 容器中的 {@link TenantProperties} 实例，
     *              与启动时传给 {@link TenantEnforcement#configure} 的应为同一实例）
     */
    public static TenantIsolationStatusView buildStatus(TenantProperties props) {
        boolean enforceEnabled = TenantEnforcement.isEnabled();
        boolean configured = TenantEnforcement.isConfigured();

        String validationStatus;
        String validationMessage;
        if (!enforceEnabled) {
            validationStatus = VALIDATION_DISABLED;
            validationMessage = "强制隔离未开启(enforce=false)：配置不参与运行期强制，无需校验；"
                    + "开启前请确保业务表 tenant_id 已正确落库并通过启动期自检";
        } else {
            try {
                TenantEnforcement.validateConfiguration();
                validationStatus = VALIDATION_OK;
                validationMessage = "配置自洽，强制隔离已生效，行级 tenant_id 约束正常运行";
            } catch (IllegalStateException e) {
                validationStatus = VALIDATION_INVALID;
                validationMessage = e.getMessage();
            }
        }

        TenantContext.TenantScope scope = TenantContext.current();
        Long currentTenantId = scope == null ? null : scope.tenantId();

        return new TenantIsolationStatusView(
                props.isEnabled(),
                enforceEnabled,
                configured,
                props.getDefaultTenantId(),
                props.getPlatformAdminRoleCode(),
                props.getHeaderName(),
                props.getClaimName(),
                props.getPlatformTables(),
                TenantContext.isSet(),
                currentTenantId,
                TenantContext.isPlatformAdmin(),
                TenantContext.isBypass(),
                validationStatus,
                validationMessage);
    }
}
