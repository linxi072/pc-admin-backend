package com.acme.scaffold.config.center.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.config.center.ConfigCenterService;
import com.acme.scaffold.config.center.dto.ConfigCenterStatusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 配置中心管理接口（R16）。
 *
 * <p>能力：运行态状态（类型 / 动态刷新支持 / 生效配置项数）、按前缀的配置快照、触发热刷新并发布变更事件。
 * 快照仅暴露白名单前缀（默认 app.*）下的配置项，避免敏感配置回显。</p>
 *
 * <p><b>装配</b>：本控制器随 {@code app.config-center.enabled=true} 才注册；关闭时完全不存在，零回归。
 * 权限码 {@code system:config-center:read} / {@code system:config-center:manage} 由
 * {@code ApiResourceAutoSyncRunner} 自动登记。</p>
 */
@Tag(name = "配置中心")
@RestController
@RequestMapping("/api/system/config-center")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.config-center", name = "enabled", havingValue = "true")
public class ConfigCenterController {

    private final ConfigCenterService service;

    @Operation(summary = "配置中心状态：类型 / 动态刷新支持 / 当前生效配置项数")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('system:config-center:read')")
    public Result<ConfigCenterStatusView> status() {
        return Result.success(new ConfigCenterStatusView(
                service.type(), service.supportsDynamicRefresh(), service.snapshotSize()));
    }

    @Operation(summary = "配置快照（按前缀过滤，默认暴露 app.* 命名空间）")
    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('system:config-center:read')")
    public Result<Map<String, String>> snapshot(
            @RequestParam(required = false) String prefix) {
        return Result.success(service.snapshot(prefix));
    }

    @Operation(summary = "触发热刷新并发布配置变更事件（订阅方可据此热重载运行态）")
    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('system:config-center:manage')")
    @AuditOperation(module = "system", type = "UPDATE", name = "配置热刷新")
    public Result<Map<String, Object>> refresh() {
        int count = service.refresh();
        return Result.success(Map.of(
                "refreshed", true,
                "effectiveCount", count,
                "dynamicRefresh", service.supportsDynamicRefresh()));
    }
}
