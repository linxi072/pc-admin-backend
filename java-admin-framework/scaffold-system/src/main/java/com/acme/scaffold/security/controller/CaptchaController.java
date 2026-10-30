package com.acme.scaffold.security.controller;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.captcha.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录验证码端点：白名单接口（无需认证），返回 token 与运算题问题。
 * 前端展示问题，用户输入答案，随 {@code /api/auth/login} 一并回传 token 与答案。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    @Operation(summary = "获取登录验证码（运算题）")
    @GetMapping("/captcha")
    public Result<CaptchaService.CaptchaView> captcha() {
        CaptchaService.Captcha c = captchaService.generate();
        return Result.success(new CaptchaService.CaptchaView(c.token(), c.question()));
    }
}
