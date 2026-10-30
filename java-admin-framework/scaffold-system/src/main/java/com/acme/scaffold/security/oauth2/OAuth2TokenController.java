package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.api.dto.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2 令牌管理接口（M43 令牌治理）。
 *
 * <p><b>权限</b>：查询取 {@code system:oauth2-token:read}，吊销取 {@code system:oauth2-token:revoke}，
 * 由 {@code ApiResourceAutoSyncRunner} 扫描 {@code @PreAuthorize} 启动时自动落库并授予 SUPER_ADMIN，
 * 无需手工登记。吊销经 {@link OAuth2TokenRevocationFilter} 在请求期实时生效。</p>
 */
@Tag(name = "OAuth2 令牌管理")
@RestController
@RequestMapping("/api/system/oauth2-tokens")
@RequiredArgsConstructor
public class OAuth2TokenController {

    private final OAuth2TokenAdminService service;

    @Operation(summary = "分页查询活动 OAuth2 令牌（可按 clientId / userId 过滤）")
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:oauth2-token:read')")
    public Result<PageResult<OAuth2TokenView>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) Long userId) {
        return Result.success(service.page(page, size, clientId, userId));
    }

    @Operation(summary = "吊销指定 jti 的 OAuth2 令牌（立即生效）")
    @PostMapping("/{jti}/revoke")
    @PreAuthorize("hasAuthority('system:oauth2-token:revoke')")
    public Result<Void> revoke(@PathVariable String jti) {
        service.revoke(jti);
        return Result.success();
    }
}
