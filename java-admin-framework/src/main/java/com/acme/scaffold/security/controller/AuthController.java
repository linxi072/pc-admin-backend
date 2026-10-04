package com.acme.scaffold.security.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.application.AuthApplicationService;
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

@Tag(name = "认证授权", description = "登录、刷新、登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;

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

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
