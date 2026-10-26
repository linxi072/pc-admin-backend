package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.tenant.TenantIgnorable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营总览看板接口（M27 报表大屏 / 后端第一刀）。
 *
 * <p><b>权限</b>：{@code system:dashboard:read}，由 {@code ApiResourceAutoSyncRunner}
 * 扫描 {@code @PreAuthorize} 自动登记并授予 SUPER_ADMIN，无需手工登记。</p>
 * <p><b>租户语义</b>：标注 {@code @TenantIgnorable}，看板为平台级全局统计，不受当前租户过滤。</p>
 */
@Tag(name = "运营总览看板")
@RestController
@RequestMapping("/api/system/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @Operation(summary = "获取运营总览快照（租户数/用户数/角色数/菜单数/在线会话/幂等记录/操作日志/今日登录）")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('system:dashboard:read')")
    @TenantIgnorable("平台级全局统计，跨租户聚合")
    public Result<DashboardOverview> overview() {
        return Result.success(service.overview());
    }
}
