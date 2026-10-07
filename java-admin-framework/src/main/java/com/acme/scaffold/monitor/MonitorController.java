package com.acme.scaffold.monitor;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.common.audit.SysOperationLogDO;
import com.acme.scaffold.system.entity.SysMonitorSampleDO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统监控接口。统一要求 monitor:read 权限，属敏感运维数据。
 */
@Tag(name = "系统监控")
@RestController
@RequestMapping("/api/system/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @Operation(summary = "实时运行指标（CPU/内存/磁盘/JVM）")
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询运行指标")
    public Result<ServerMetrics> metrics() {
        return Result.success(monitorService.currentMetrics());
    }

    @Operation(summary = "在线用户与会话汇总")
    @GetMapping("/online-summary")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<Map<String, Object>> onlineSummary() {
        return Result.success(monitorService.onlineSummary());
    }

    @Operation(summary = "在线会话明细（可按用户名/姓名筛选）")
    @GetMapping("/online-sessions")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询在线会话")
    public Result<List<OnlineSessionView>> onlineSessions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(monitorService.onlineSessions(keyword, limit));
    }

    @Operation(summary = "异常日志分页（按时间范围查询关键接口/任务失败记录）")
    @GetMapping("/error-logs")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询异常日志")
    public Result<PageResult<SysOperationLogDO>> errorLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String keyword) {
        return Result.success(monitorService.errorLogs(page, size, from, to, module, keyword));
    }

    @Operation(summary = "异常日志按模块/错误码聚合")
    @GetMapping("/error-summary")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<List<Map<String, Object>>> errorSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(monitorService.errorSummary(from, to));
    }

    @Operation(summary = "历史采样趋势（按时间范围）")
    @GetMapping("/samples")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<List<SysMonitorSampleDO>> samples(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "200") int limit) {
        return Result.success(monitorService.samples(from, to, limit));
    }

    @Operation(summary = "手动触发一次采样（供前端刷新按钮使用）")
    @PostMapping("/sample")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "system", type = "UPDATE", name = "手动采集监控指标")
    public Result<Map<String, Object>> sample() {
        return Result.success(Map.of("inserted", monitorService.sampleOnce()));
    }
}
