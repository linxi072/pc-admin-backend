package com.acme.scaffold.system.controller;

import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.audit.AuditOperation;
import com.acme.scaffold.system.dto.ForceLogoutRequest;
import com.acme.scaffold.system.dto.LoginRestrictionView;
import com.acme.scaffold.system.dto.OnlineUserQuery;
import com.acme.scaffold.system.dto.OnlineUserView;
import com.acme.scaffold.system.dto.RestrictLoginRequest;
import com.acme.scaffold.system.service.OnlineUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 在线用户管理接口（V27，系统管理模块）。
 *
 * <p>权限码三分：读（{@code system:online:read}）、强制下线
 * （{@code system:online:force-logout}）、限制登录（{@code system:online:restrict}），
 * 与 V27 迁移的 sys_api_resource 种子一一对应。
 *
 * <p>写操作均经 {@link AuditOperation} 落审计日志（operator / IP / traceId / 入参摘要），
 * 限制记录本身另结构化沉淀于 sys_login_restriction，双重可追溯。
 */
@Tag(name = "在线用户管理")
@RestController
@RequestMapping("/api/system/online-users")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    @Operation(summary = "分页查询在线会话（账号/IP 模糊 + 状态筛选，默认按最近活跃倒序）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:online:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询在线用户列表")
    public Result<PageResult<OnlineUserView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String status) {
        return Result.success(onlineUserService.page(
                new OnlineUserQuery(page, size, username, ip, status)));
    }

    @Operation(summary = "分页查询登录限制记录（生效中/已解除）")
    @GetMapping("/restrictions/page")
    @PreAuthorize("hasAuthority('system:online:read')")
    @AuditOperation(module = "system", type = "QUERY", name = "查询登录限制记录")
    public Result<PageResult<LoginRestrictionView>> restrictionPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status) {
        return Result.success(onlineUserService.restrictionPage(page, size, username, status));
    }

    @Operation(summary = "批量强制下线在线会话（吊销刷新令牌 + 会话置离线，设备侧下一个请求即 401）")
    @PostMapping("/force-logout")
    @PreAuthorize("hasAuthority('system:online:force-logout')")
    @AuditOperation(module = "system", type = "FORCE_LOGOUT", name = "强制下线在线会话")
    public Result<Integer> forceLogout(@Valid @RequestBody ForceLogoutRequest request) {
        return Result.success(onlineUserService.forceLogout(request.sessionIds()));
    }

    @Operation(summary = "设置临时限制登录（限时+原因，到期自动解除；同步强制下线其在线会话）")
    @PostMapping("/users/{userId}/restrict")
    @PreAuthorize("hasAuthority('system:online:restrict')")
    @AuditOperation(module = "system", type = "RESTRICT", name = "设置临时限制登录")
    public Result<String> restrict(@PathVariable Long userId,
                                   @Valid @RequestBody RestrictLoginRequest request) {
        return Result.success(String.valueOf(onlineUserService.restrict(userId, request)));
    }

    @Operation(summary = "手动解除登录限制（幂等，解除该用户全部生效中的限制）")
    @PostMapping("/users/{userId}/release")
    @PreAuthorize("hasAuthority('system:online:restrict')")
    @AuditOperation(module = "system", type = "RELEASE", name = "解除登录限制")
    public Result<Integer> release(@PathVariable Long userId) {
        return Result.success(onlineUserService.release(userId));
    }
}
