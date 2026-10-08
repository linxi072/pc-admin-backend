package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditLogQuery;
import com.acme.scaffold.common.audit.AuditLogService;
import com.acme.scaffold.common.audit.AuditLogSummary;
import com.acme.scaffold.common.audit.AuditLogView;
import com.acme.scaffold.security.context.SecurityContextFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志接口（设计方案 §20：审计日志可按 traceId 查询且无敏感字段）。
 *
 * <p><b>权限说明</b>：审计日志属于高敏感数据，读写分离之外再细分「摘要可见性」——
 * 持有 {@code system:audit:read} 可查列表，但摘要一律脱敏；仅持有 {@code system:audit:sensitive}
 * 时回传未脱敏摘要（排障场景）。IP 无论何种权限均掩码，避免个人轨迹外泄。
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/api/system/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final SecurityContextFacade securityContextFacade;

    @Operation(summary = "分页查询审计日志（支持模块/操作类型/操作人/结果/traceId/关键词/时间区间）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:audit:read')")
    public Result<PageResult<AuditLogView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long minDurationMs,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        AuditLogQuery query = new AuditLogQuery(page, size, moduleCode, operationType, operatorId, operatorName,
                success, traceId, keyword, minDurationMs, startTime, endTime, sortField, sortDirection);
        return Result.success(auditLogService.query(query, sensitiveVisible()));
    }

    @Operation(summary = "按 traceId 查询一次请求的完整操作链路")
    @GetMapping("/trace/{traceId}")
    @PreAuthorize("hasAuthority('system:audit:read')")
    public Result<List<AuditLogView>> trace(@PathVariable String traceId) {
        return Result.success(auditLogService.findByTraceId(traceId, sensitiveVisible()));
    }

    @Operation(summary = "审计日志统计摘要（总数/成功/失败/慢操作/平均与最大耗时）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:audit:read')")
    public Result<AuditLogSummary> summary(
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        AuditLogQuery query = new AuditLogQuery(1, 1, moduleCode, operationType, null, null, null, null,
                keyword, null, startTime, endTime, null, null);
        return Result.success(auditLogService.summary(query));
    }

    private boolean sensitiveVisible() {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> p.hasPermission("system:audit:sensitive"))
                .orElse(false);
    }
}
