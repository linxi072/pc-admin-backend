package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.tenant.TenantIsolationRules;
import com.acme.scaffold.common.tenant.TenantIsolationStatusView;
import com.acme.scaffold.common.tenant.TenantProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户隔离态势诊断接口（M28）：只读暴露当前多租户隔离的「配置态 + 运行态 + 当前执行作用域 + 配置校验结果」，
 * 供运维诊断页展示。
 *
 * <p><b>只读语义</b>：本接口不写库、不触发任何隔离逻辑（不调用 {@code JooqWriters} 写路径）、不新增依赖，
 * 仅聚合既有 {@link TenantEnforcement} / {@link TenantContext} / {@link TenantProperties} 的静态与配置态。</p>
 *
 * <p><b>权限</b>：取 {@code system:tenant-isolation:read}——由 {@code ApiResourceAutoSyncRunner} 启动时扫描
 * {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN，无需手工登记（与 V52 注释一致）。</p>
 */
@Tag(name = "租户隔离态势")
@RestController
@RequestMapping("/api/system/tenant-isolation")
@RequiredArgsConstructor
public class TenantIsolationStatusController {

    private final TenantProperties tenantProperties;

    @Operation(summary = "查询租户隔离态势（只读诊断）")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('system:tenant-isolation:read')")
    public Result<TenantIsolationStatusView> status() {
        return Result.success(TenantIsolationRules.buildStatus(tenantProperties));
    }
}
