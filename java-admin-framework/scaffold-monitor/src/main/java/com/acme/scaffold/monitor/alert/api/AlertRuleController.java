package com.acme.scaffold.monitor.alert.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.monitor.alert.service.AlertRuleService;
import com.acme.scaffold.monitor.dto.AlertRuleCreateRequest;
import com.acme.scaffold.monitor.dto.AlertRuleUpdateRequest;
import com.acme.scaffold.monitor.dto.AlertRuleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

/**
 * 告警规则管理接口（{@code /api/monitoring/alert-rules}）。
 *
 * <p><b>权限</b>：读取复用 {@code system:monitor:read}（与告警收件箱同权）；
 * 写操作（增删改 / 启停 / 立即评估）使用独立权限码 {@code system:monitor:rule:manage}，
 * 由 {@code ApiResourceAutoSyncRunner} 启动时扫描 {@code @PreAuthorize} 自动登记并授予 SUPER_ADMIN。</p>
 */
@Tag(name = "告警规则管理")
@RestController
@RequestMapping("/api/monitoring/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService ruleService;

    @Operation(summary = "告警规则列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<List<AlertRuleView>> list() {
        return Result.success(ruleService.list());
    }

    @Operation(summary = "告警规则详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:monitor:read')")
    public Result<AlertRuleView> get(@PathVariable Long id) {
        return Result.success(ruleService.get(id));
    }

    @Operation(summary = "新增告警规则")
    @PostMapping
    @PreAuthorize("hasAuthority('system:monitor:rule:manage')")
    @AuditOperation(module = "monitor", type = "CREATE", name = "新增告警规则")
    public Result<AlertRuleView> create(@RequestBody AlertRuleCreateRequest req) {
        return Result.success(ruleService.create(req));
    }

    @Operation(summary = "修改告警规则")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:monitor:rule:manage')")
    @AuditOperation(module = "monitor", type = "UPDATE", name = "修改告警规则")
    public Result<AlertRuleView> update(@PathVariable Long id, @RequestBody AlertRuleUpdateRequest req) {
        return Result.success(ruleService.update(id, req));
    }

    @Operation(summary = "删除告警规则（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:monitor:rule:manage')")
    @AuditOperation(module = "monitor", type = "DELETE", name = "删除告警规则")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启停告警规则")
    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('system:monitor:rule:manage')")
    @AuditOperation(module = "monitor", type = "UPDATE", name = "启停告警规则")
    public Result<AlertRuleView> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.success(ruleService.setEnabled(id, enabled));
    }

    @Operation(summary = "立即评估一次告警规则（手动触发，返回本次新触发条数）")
    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('system:monitor:rule:manage')")
    @AuditOperation(module = "monitor", type = "QUERY", name = "手动评估告警规则")
    public Result<Integer> evaluate() {
        return Result.success(ruleService.evaluateTick(java.time.Instant.now()));
    }
}
