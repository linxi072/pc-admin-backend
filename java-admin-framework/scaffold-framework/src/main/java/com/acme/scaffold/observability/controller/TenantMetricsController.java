package com.acme.scaffold.observability.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.observability.dto.TenantMetricView;
import com.acme.scaffold.observability.service.TenantMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * 租户维度可观测接口（R14 租户维度）：按租户聚合的请求量 / 错误率 / 平均时延，供 APM 大盘下钻。
 *
 * <p><b>权限</b>：读接口需 {@code system:observability:read}（与既有可观测接口一致），
 * 权限码由 {@code ApiResourceAutoSyncRunner} 自动登记并授予 SUPER_ADMIN。</p>
 */
@Tag(name = "租户维度可观测")
@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class TenantMetricsController {

    private final TenantMetricsService tenantMetricsService;

    @Operation(summary = "租户维度聚合（按请求量降序）：请求量 / 错误率 / 平均时延")
    @GetMapping("/tenant-metrics")
    @PreAuthorize("hasAuthority('system:observability:read')")
    public Result<List<TenantMetricView>> tenantMetrics() {
        return Result.success(tenantMetricsService.snapshot());
    }
}
