package com.acme.scaffold.common.tenant;

import java.util.List;

/**
 * 租户隔离态势快照（M28 只读运维诊断）：聚合「配置态 + 运行态 + 当前执行作用域 + 配置校验结果」，
 * 供前端「租户隔离态势」诊断页只读展示，不写库、不触发任何隔离逻辑。
 *
 * <p>字段语义分四类：
 * <ol>
 *   <li>配置态：隔离体系开关、强制隔离开关、默认租户、平台管理员角色码、解析源（请求头/Token 声明）、平台维表；</li>
 *   <li>运行态：配置快照是否已注入（{@link TenantEnforcement#configure} 启动时是否调用）；</li>
 *   <li>当前执行作用域：当前请求线程是否设置租户、当前租户 ID、是否平台管理员、是否跨租户旁路；</li>
 *   <li>配置校验：{@code OK}（开启且自洽）/ {@code INVALID}（开启但配置非法，含修正建议）/ {@code DISABLED}（未开启，不校验）。</li>
 * </ol>
 */
public record TenantIsolationStatusView(

        /** 多租户隔离总开关（app.tenant.enabled：是否启用隔离体系）。 */
        boolean isolationEnabled,

        /** 强制隔离运行态（app.tenant.enforce：行级 tenant_id 注入是否生效）。 */
        boolean enforceEnabled,

        /** 配置快照是否已注入（TenantEnforcement.configure 是否被启动时调用）。 */
        boolean configured,

        /** 默认租户 ID（未解析到租户时回落值）。 */
        Long defaultTenantId,

        /** 平台管理员角色码（命中即视为平台管理员作用域，可跨租户）。 */
        String platformAdminRoleCode,

        /** 租户请求头名称（显式覆盖当前租户）。 */
        String headerName,

        /** JWT/Token 中承载租户 ID 的声明名。 */
        String claimName,

        /** 平台共享维表（豁免隔离的表集合，跨租户全局可见）。 */
        List<String> platformTables,

        /** 当前请求线程是否已设置租户作用域。 */
        boolean scopeSet,

        /** 当前执行作用域的租户 ID（未设置作用域时为 null）。 */
        Long currentTenantId,

        /** 当前执行作用域是否平台管理员。 */
        boolean currentPlatformAdmin,

        /** 当前执行作用域是否处于跨租户旁路（@TenantIgnorable 作用域）。 */
        boolean currentBypass,

        /** 配置校验状态：OK / INVALID / DISABLED。 */
        String validationStatus,

        /** 配置校验说明（INVALID 时含可操作修正建议）。 */
        String validationMessage) {
}
