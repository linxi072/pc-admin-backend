package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.tenant.TenantIsolationAuditReport;
import com.acme.scaffold.common.tenant.TenantIsolationAuditRules;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.system.tenant.dto.TenantDataOverview;
import com.acme.scaffold.system.tenant.dto.TenantOverviewItem;
import com.acme.scaffold.system.tenant.dto.TenantOverviewView;
import com.acme.scaffold.system.tenant.dto.TenantView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 跨租户运营总览服务（M46 只读聚合）。
 *
 * <p>将分散在多个端点的多租户运行状态聚合成单一只读快照，供运营一眼看清：
 * enforce 开关、租户清单与启用数、每租户数据量、存量隔离自检结论。
 * 各子源独立 fail-open——任一来源异常时回落默认值（空列表 / null 报告），
 * 不让单点故障阻断整页加载（与 M45 自检服务的稳健性策略一致）。</p>
 *
 * <p>本服务不直接标注 {@code @TenantIgnorable}：所依赖的 {@code TenantGovernanceService#dataOverview()}
 * 与 {@code TenantIsolationAuditService#scanViolations()} 内部已各自声明跨租户旁路，
 * 本服务仅在平台租户维度表（sys_tenant）查询，隔离生效时也不被过滤。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOverviewService {

    private final TenantService tenantService;
    private final TenantGovernanceService governanceService;
    private final TenantIsolationAuditService isolationAuditService;
    private final TenantProperties tenantProperties;

    /**
     * 构建运营总览快照。
     */
    public TenantOverviewView buildOverview() {
        // 1) 租户列表（元数据）
        List<TenantView> tenants = safe(() -> tenantService.list(), List.<TenantView>of());
        // 2) 每租户数据概览（各业务表行数）
        Map<Long, TenantDataOverview> dataByTenant = new HashMap<>();
        for (TenantDataOverview d : safe(() -> governanceService.dataOverview(), List.<TenantDataOverview>of())) {
            dataByTenant.put(d.tenantId(), d);
        }
        // 3) 存量隔离自检（M45 报告）
        TenantIsolationAuditReport audit = safe(() ->
                TenantIsolationAuditRules.audit(
                        isolationAuditService.scanViolations(),
                        TenantTables.businessTableNames().size()),
                null);

        // 4) 组装每租户摘要
        List<TenantOverviewItem> items = new ArrayList<>();
        for (TenantView t : tenants) {
            TenantDataOverview d = dataByTenant.get(t.id());
            items.add(new TenantOverviewItem(
                    t.id(), t.tenantCode(), t.tenantName(), t.status(), t.expireAt(),
                    d == null ? 0L : d.totalRows(),
                    d == null ? Map.of() : d.tableCounts()));
        }

        long enabled = tenants.stream().filter(t -> "ENABLED".equals(t.status())).count();
        return new TenantOverviewView(
                tenantProperties.isEnforce(),
                tenants.size(),
                (int) enabled,
                TenantTables.businessTableNames().size(),
                audit,
                items,
                LocalDateTime.now());
    }

    /**
     * 子源 fail-open 包装：执行失败时回落 {@code fallback}，并记录告警，
     * 避免单点异常阻断整页总览。
     */
    private static <T> T safe(Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("运营总览子源聚合失败，回落默认值：{}", e.toString());
            return fallback;
        }
    }
}
