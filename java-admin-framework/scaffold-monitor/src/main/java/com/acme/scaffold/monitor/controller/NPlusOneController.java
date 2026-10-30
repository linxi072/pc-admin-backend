package com.acme.scaffold.monitor.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.monitor.dto.NPlusOneStatusView;
import com.acme.scaffold.monitor.dto.NPlusOneSuspectView;
import com.acme.scaffold.monitor.service.NPlusOneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * N+1 查询探测接口。与 {@link MonitorController} 统一要求 monitor:read 权限，属敏感运维数据。
 *
 * <p>探测数据来源：jOOQ 执行链路上的 {@code NPlusOneListener}（JooqConfig / DataSourceConfig
 * 双路装配）在「同构 SQL 单线程内执行达阈值」时向有界留存登记事件——日志告警的一次性信息
 * 在此变为可查询、可复盘、可清理的运行态数据。</p>
 */
@Tag(name = "N+1 查询探测")
@RestController
@RequestMapping("/api/system/monitor/n-plus-one")
@RequiredArgsConstructor
public class NPlusOneController {

    private final NPlusOneService nPlusOneService;

    @Operation(summary = "N+1 疑似事件列表（最新在前）")
    @GetMapping("/suspects")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询 N+1 疑似事件")
    public Result<List<NPlusOneSuspectView>> suspects(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(nPlusOneService.recentSuspects(limit));
    }

    @Operation(summary = "N+1 探测运行态（阈值/容量/当前条数/累计越限次数）")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<NPlusOneStatusView> status() {
        return Result.success(nPlusOneService.status());
    }

    @Operation(summary = "清空 N+1 疑似事件留存（累计计数不清零）")
    @DeleteMapping("/suspects")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "system", type = "UPDATE", name = "清空 N+1 疑似事件留存")
    public Result<Boolean> clear() {
        return Result.success(nPlusOneService.clear());
    }
}
