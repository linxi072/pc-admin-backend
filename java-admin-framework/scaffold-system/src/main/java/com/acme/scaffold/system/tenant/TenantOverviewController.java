package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.system.tenant.dto.TenantOverviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨租户运营总览接口（M46 只读聚合）。
 *
 * <p>权限沿用租户查询的 {@code system:tenant:read}（与「租户管理」列表同源），
 * 由 {@code ApiResourceAutoSyncRunner} 启动时扫描 {@code @PreAuthorize} 自动落库并授予 SUPER_ADMIN。
 * 该端点只读聚合既有能力，不引入新写操作、不新增权限码。</p>
 */
@Tag(name = "租户运营总览")
@RestController
@RequestMapping("/api/system/tenants")
@RequiredArgsConstructor
public class TenantOverviewController {

    private final TenantOverviewService overviewService;

    @Operation(summary = "跨租户运营总览（只读聚合：enforce 开关 / 租户清单 / 数据量 / 存量隔离自检）")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('system:tenant:read')")
    public Result<TenantOverviewView> overview() {
        return Result.success(overviewService.buildOverview());
    }
}
