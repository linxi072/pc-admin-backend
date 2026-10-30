package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.tenant.TenantIgnorable;
import com.acme.scaffold.system.tenant.dto.BackfillTenantRequest;
import com.acme.scaffold.system.tenant.dto.ReassignTenantRequest;
import com.acme.scaffold.system.tenant.dto.TenantDataOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 租户治理控制器（M19 正式立项 / 平台跨租户治理通道）。
 *
 * <p>所有端点标注 {@code @TenantIgnorable}：在跨租户旁路作用域下执行，无论 {@code enforce} 开关
 * 是否开启，均能对全部业务表执行治理动作。权限沿用租户管理的 {@code system:tenant:manage}，
 * 由 {@code ApiResourceAutoSyncRunner} 自动登记并授予 SUPER_ADMIN。</p>
 */
@RestController
@RequestMapping("/api/system/tenants")
@RequiredArgsConstructor
public class TenantGovernanceController {

    private final TenantGovernanceService governanceService;

    /** 跨租户数据总览：按租户聚合各业务表行数。 */
    @TenantIgnorable("平台跨租户数据总览")
    @GetMapping("/data-overview")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    public List<TenantDataOverview> dataOverview() {
        return governanceService.dataOverview();
    }

    /** 存量数据归位：将全部业务表的历史数据归位到目标租户。 */
    @TenantIgnorable("存量数据归位到指定租户")
    @PostMapping("/backfill")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    public Map<String, Object> backfill(@RequestBody BackfillTenantRequest request) {
        int affected = governanceService.backfill(request.getTargetTenantId());
        return Map.of("targetTenantId", request.getTargetTenantId(), "affectedRows", affected);
    }

    /** 跨租户数据迁移：将指定表从源租户迁移到目标租户。 */
    @TenantIgnorable("跨租户数据迁移")
    @PostMapping("/reassign")
    @PreAuthorize("hasAuthority('system:tenant:manage')")
    public Map<String, Object> reassign(@RequestBody ReassignTenantRequest request) {
        int affected = governanceService.reassign(
                request.getFromTenantId(), request.getToTenantId(), request.getTables());
        return Map.of("affectedRows", affected);
    }
}
