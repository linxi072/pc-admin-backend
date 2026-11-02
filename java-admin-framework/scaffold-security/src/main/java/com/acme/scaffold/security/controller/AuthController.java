package com.acme.scaffold.security.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.web.ClientIpResolver;
import com.acme.scaffold.security.service.AuthService;
import com.acme.scaffold.security.dto.AuthChangePasswordRequest;
import com.acme.scaffold.security.dto.ForgotPasswordRequest;
import com.acme.scaffold.security.dto.LoginCommand;
import com.acme.scaffold.security.dto.RefreshCommand;
import com.acme.scaffold.security.vo.TokenView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证授权控制器（系统安全入口）。
 *
 * <p>暴露登录、刷新令牌、登出、会话无关改密与找回密码等认证端点；
 * 所有端点统一经 {@code GlobalExceptionHandler} 包络为 {@link Result}，
 * 由 {@link AuthService} 承载实际的凭证校验与会话管理。
 */
@Tag(name = "认证授权", description = "登录、刷新、登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authApplicationService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public Result<TokenView> login(@Valid @RequestBody LoginCommand command, HttpServletRequest request) {
        String ip = clientIp(request);
        String ua = request.getHeader("User-Agent");
        String device = request.getHeader("X-Device-Id");
        return Result.success(authApplicationService.login(command, ip, ua, device));
    }

    @Operation(summary = "用刷新令牌换取新的访问令牌（同时轮换刷新令牌）")
    @PostMapping("/refresh")
    public Result<TokenView> refresh(@Valid @RequestBody RefreshCommand command) {
        return Result.success(authApplicationService.refresh(command));
    }

    @Operation(summary = "登出（吊销当前用户所有刷新令牌）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authApplicationService.logout();
        return Result.success();
    }

    @Operation(summary = "会话无关改密（强制改密闭环）：以用户名+原密码认证后修改密码，无需已登录会话")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody AuthChangePasswordRequest request) {
        authApplicationService.changePassword(request.username(), request.oldPassword(), request.newPassword());
        return Result.success();
    }

    @Operation(summary = "忘记密码自助重置：以用户名+手机号/邮箱核验后重置密码，无需原密码")
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authApplicationService.forgotPassword(request.username(), request.newPassword(),
                request.mobile(), request.email());
        return Result.success();
    }

    /** 客户端 IP：经 {@link ClientIpResolver} 按可信代理跳数解析，避免伪造 X-Forwarded-For。 */
    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
