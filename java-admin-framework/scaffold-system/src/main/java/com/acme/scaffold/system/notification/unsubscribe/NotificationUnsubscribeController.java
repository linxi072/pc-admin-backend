package com.acme.scaffold.system.notification.unsubscribe;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知退订接口（M15）：邮件/短信正文中的签名链接目标，<b>免登录</b>。
 *
 * <p><b>鉴权约定</b>：与 {@code /api/auth/login} 同范式——本端点在
 * {@code SecurityConfig.BASE_PUBLIC_API} 白名单放行 GET。外部浏览器点击邮件链接时
 * 无法出示 JWT，鉴权由令牌的 HMAC-SHA256 签名 + 过期时间承担（见
 * {@code UnsubscribeTokenRules}）；控制器另加 IP 级 {@link RateLimit} 防链接爆破。
 * 写操作仅限令牌承载的 (用户 × 渠道 × 类型)，不接受任何用户标识入参，无越权面。</p>
 *
 * <p><b>审计</b>：退订动作本身即一条 {@code sys_notification_preference} 行写入
 * （{@code enabled=0} + 时间戳），与个人中心订阅变更同库同口径，无需额外审计表；
 * 令牌不落库（无状态），链接失效仅由过期时间决定。</p>
 */
@Tag(name = "通知退订")
@RestController
@RequestMapping(NotificationUnsubscribeService.UNSUBSCRIBE_PATH)
@RequiredArgsConstructor
public class NotificationUnsubscribeController {

    private final NotificationUnsubscribeService unsubscribeService;

    @Operation(summary = "退订通知（邮件/短信正文链接，免登录，凭签名令牌）")
    @GetMapping
    @RateLimit(max = 20, windowSeconds = 60, scope = RateLimit.Scope.IP)
    public Result<NotificationUnsubscribeService.UnsubscribeOutcome> unsubscribe(
            @RequestParam String token) {
        return Result.success(unsubscribeService.unsubscribe(token));
    }
}
