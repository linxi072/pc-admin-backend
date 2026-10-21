package com.acme.scaffold.observability.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.observability.dto.MetricView;
import com.acme.scaffold.observability.dto.SpanView;
import com.acme.scaffold.observability.dto.TraceSummaryView;
import com.acme.scaffold.observability.service.ObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可观测性管理接口：span 本地回溯、运行态摘要与指标快照。
 *
 * <p>能力：最近 span 列表（按状态 / traceId 过滤）、按 traceId 检索同一追踪、
 * 清空本地留存、记录器与导出器双链路摘要、Micrometer 指标快照（按名称前缀过滤）。
 * span 由 {@code OtlpTracingFilter} 在请求级采集，本地留存受
 * {@code observability.recorder.enabled} 控制（默认开启，容量 200）。</p>
 *
 * <p><b>权限</b>：读接口需 {@code system:observability:read}；清空（写）接口需
 * {@code system:observability:manage}。权限码由 {@code ApiResourceScanner} 自动登记到
 * {@code sys_api_resource} 并授予 SUPER_ADMIN，同时登记 {@code sys_menu} 菜单
 * （route=/observability/traces）以驱动侧边栏可见性。</p>
 */
@Tag(name = "可观测性管理")
@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    @Operation(summary = "最近 span 列表（最新在前），支持按状态与 traceId 过滤")
    @GetMapping("/traces")
    @PreAuthorize("hasAuthority('system:observability:read')")
    public Result<List<SpanView>> traces(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceId) {
        return Result.success(observabilityService.recentSpans(limit, status, traceId));
    }

    @Operation(summary = "按 traceId 检索同一追踪的全部 span（时间正序）")
    @GetMapping("/traces/{traceId}")
    @PreAuthorize("hasAuthority('system:observability:read')")
    public Result<List<SpanView>> traceBy(@PathVariable String traceId) {
        return Result.success(observabilityService.spansByTraceId(traceId));
    }

    @Operation(summary = "可观测性运行态摘要：记录器与 OTLP 导出器双链路开关及留存统计")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:observability:read')")
    public Result<TraceSummaryView> summary() {
        return Result.success(observabilityService.summary());
    }

    @Operation(summary = "Micrometer 指标快照，支持按名称前缀过滤")
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('system:observability:read')")
    public Result<List<MetricView>> metrics(@RequestParam(required = false) String prefix) {
        return Result.success(observabilityService.metricSnapshot(prefix));
    }

    @Operation(summary = "清空本地 span 留存（不影响 OTLP 导出链路）")
    @DeleteMapping("/traces")
    @PreAuthorize("hasAuthority('system:observability:manage')")
    public Result<Integer> clearTraces() {
        return Result.success(observabilityService.clearSpans());
    }
}
