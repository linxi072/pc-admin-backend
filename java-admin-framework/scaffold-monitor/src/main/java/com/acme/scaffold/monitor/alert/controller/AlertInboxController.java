package com.acme.scaffold.monitor.alert.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.monitor.alert.service.AlertInboxService;
import com.acme.scaffold.monitor.dto.AlertInboxItemView;
import com.acme.scaffold.monitor.dto.AlertInboxStatsView;
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
 * 告警收件箱管理接口（{@code /api/monitoring/alerts/inbox}）。
 *
 * <p><b>定位</b>：Alertmanager webhook 的<b>接收</b>入口是
 * {@link com.acme.scaffold.monitor.alert.AlertWebhookController}（令牌鉴权、
 * 面向机器）；本控制器是<b>管理</b>入口（RBAC 权限、面向运维人员），
 * 两者共用同一内存收件箱，读写分离。</p>
 *
 * <p><b>权限</b>：统一 {@code system:monitor:read}——与 {@code MonitorController}、
 * {@code NPlusOneController} 同权（监控数据清空属运维只读角色常规操作，
 * 与 N+1 疑似事件清空同口径）。权限码无需手工播种——
 * {@code ApiResourceAutoSyncRunner} 启动时扫描 {@code @PreAuthorize}
 * 自动登记 {@code sys_api_resource} 并授予 SUPER_ADMIN。</p>
 *
 * <p><b>与接收链路的关系</b>：本控制器不参与告警接收，只读快照 + 整体清空，
 * 详见 {@link AlertInboxService} 类注释中「为何不提供单条删除」的论证。</p>
 */
@Tag(name = "告警收件箱管理")
@RestController
@RequestMapping("/api/monitoring/alerts/inbox")
@RequiredArgsConstructor
public class AlertInboxController {

    private final AlertInboxService alertInboxService;

    @Operation(summary = "收件箱列表（可按 severity/status 筛选，最新在前，limit 按容量自适应钳制）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "monitor", type = "QUERY", name = "查询告警收件箱")
    public Result<List<AlertInboxItemView>> list(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        return Result.success(alertInboxService.list(severity, status, limit));
    }

    @Operation(summary = "收件箱汇总（容量/总数/状态分布/severity 分布/最新与最旧留存时间）")
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<AlertInboxStatsView> stats() {
        return Result.success(alertInboxService.stats());
    }

    @Operation(summary = "清空收件箱（整体丢弃当前窗口，返回清理条数；已发布的事件不受影响）")
    @DeleteMapping
    @PreAuthorize("hasAuthority('system:monitor:read')")
    @AuditOperation(module = "monitor", type = "DELETE", name = "清空告警收件箱")
    public Result<Integer> clear() {
        return Result.success(alertInboxService.clear());
    }
}
