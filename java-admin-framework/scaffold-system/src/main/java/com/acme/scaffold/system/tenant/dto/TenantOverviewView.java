package com.acme.scaffold.system.tenant.dto;

import com.acme.scaffold.common.tenant.TenantIsolationAuditReport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨租户运营总览（M46 只读聚合视图）：将分散在多个端点的多租户运行状态聚合为单一只读快照，
 * 使平台运营可一眼看清多租户状态。
 *
 * <p>聚合来源（任一失败均 fail-open，不影响其余部分）：
 * <ul>
 *   <li>{@code enforce}：{@link com.acme.scaffold.common.tenant.TenantProperties#isEnforce()} 运行时开关；</li>
 *   <li>租户列表 / 启用数：{@code TenantService#list()}；</li>
 *   <li>每租户数据量：{@code TenantGovernanceService#dataOverview()}（跨租户旁路统计各业务表行数）；</li>
 *   <li>隔离自检结论：复用 M45 {@code TenantIsolationAuditRules#audit(...)} 产出的报告。</li>
 * </ul>
 *
 * @param enforce               是否开启多租户强制隔离（enforce 开关实际生效态）
 * @param tenantCount          租户总数（含停用）
 * @param enabledTenantCount   启用（ENABLED）租户数
 * @param businessTableCount   纳入隔离检查的业务表总数
 * @param isolationAudit       存量隔离自检报告（M45），不可用时为 null
 * @param tenants              各租户摘要（元数据 + 数据量）
 * @param generatedAt          快照生成时间
 */
public record TenantOverviewView(
        boolean enforce,
        int tenantCount,
        int enabledTenantCount,
        int businessTableCount,
        TenantIsolationAuditReport isolationAudit,
        List<TenantOverviewItem> tenants,
        LocalDateTime generatedAt) {
}
