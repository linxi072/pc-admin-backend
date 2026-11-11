package com.acme.scaffold.chaos.controller;

import com.acme.scaffold.chaos.ChaosService;
import com.acme.scaffold.chaos.dto.ChaosArmRequest;
import com.acme.scaffold.chaos.model.ChaosFault;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * 混沌工程运维接口（R15）：查看 / 注入 / 解除故障规则。
 *
 * <p>本控制器随 {@code app.chaos.enabled=true} 才注册；关闭时完全不存在，零回归。
 * 权限码 {@code system:chaos:read} / {@code system:chaos:manage} 由 {@code ApiResourceAutoSyncRunner}
 * 自动登记并授予 SUPER_ADMIN；注入/清除带 {@code @AuditOperation} 留痕。</p>
 */
@Tag(name = "混沌工程")
@RestController
@RequestMapping("/api/system/chaos")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.chaos", name = "enabled", havingValue = "true")
public class ChaosController {

    private final ChaosService chaosService;

    @Operation(summary = "当前全部已注入的故障规则")
    @GetMapping("/faults")
    @PreAuthorize("hasAuthority('system:chaos:read')")
    public Result<List<ChaosFault>> list() {
        return Result.success(chaosService.list());
    }

    @Operation(summary = "注入一条故障规则（LATENCY / ERROR / EXCEPTION）")
    @PostMapping("/faults")
    @PreAuthorize("hasAuthority('system:chaos:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "混沌故障注入")
    public Result<ChaosFault> arm(@RequestBody ChaosArmRequest request) {
        return Result.success(chaosService.arm(request));
    }

    @Operation(summary = "按 id 解除一条故障规则")
    @DeleteMapping("/faults/{id}")
    @PreAuthorize("hasAuthority('system:chaos:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "混沌故障解除")
    public Result<Void> disarm(@PathVariable String id) {
        chaosService.disarm(id);
        return Result.success(null);
    }

    @Operation(summary = "清除全部故障规则（演练结束恢复常态）")
    @DeleteMapping("/faults")
    @PreAuthorize("hasAuthority('system:chaos:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "混沌故障全部清除")
    public Result<Void> clear() {
        chaosService.clear();
        return Result.success(null);
    }
}
