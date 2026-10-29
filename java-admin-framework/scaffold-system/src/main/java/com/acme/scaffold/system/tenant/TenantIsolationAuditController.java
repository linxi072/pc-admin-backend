package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.common.tenant.TenantIsolationAuditReport;
import com.acme.scaffold.common.tenant.TenantIsolationAuditRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存量隔离自检接口（M45）：只读暴露全部业务表 tenant_id 归属情况与安全开启判定，
 * 供运维在开启多租户强制隔离（enforce）前预检。
 *
 * <p>复用 M28 的 {@code /api/system/tenant-isolation} 诊断命名空间与 {@code system:tenant-isolation:read}
 * 权限（由 {@code ApiResourceAutoSyncRunner} 自动登记并授予 SUPER_ADMIN），无需新增菜单种子。</p>
 */
@Tag(name = "租户隔离自检")
@RestController
@RequestMapping("/api/system/tenant-isolation")
@RequiredArgsConstructor
public class TenantIsolationAuditController {

    private final TenantIsolationAuditService auditService;

    @Operation(summary = "存量隔离自检（安全开启 enforce 预检）")
    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('system:tenant-isolation:read')")
    public Result<TenantIsolationAuditReport> audit() {
        return Result.success(TenantIsolationAuditRules.audit(
                auditService.scanViolations(), TenantTables.businessTableNames().size()));
    }
}
