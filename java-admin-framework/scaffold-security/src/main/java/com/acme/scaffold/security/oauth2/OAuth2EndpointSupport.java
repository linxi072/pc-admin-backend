package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * OAuth2 端点共享静态支撑方法。
 *
 * <p>协议级端点（{@code /oauth2/token}、{@code /oauth2/authorize}、{@code /oauth2/consent}）原本各自
 * 内联了一份完全相同的 OAuth2 错误响应构造与当前登录主体解析逻辑；此处收敛为单一事实来源，
 * 各端点保留同名私有方法作为委托壳（调用方零改动）。</p>
 */
final class OAuth2EndpointSupport {

    private OAuth2EndpointSupport() {
    }

    /** 构造标准 OAuth2 错误响应（{@code error} / {@code error_description}）。 */
    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", desc);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** 从 {@link SecurityContextHolder} 解析已认证的 {@link CurrentPrincipal}，未认证时返回 {@code null}。 */
    static CurrentPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentPrincipal p) {
            return p;
        }
        return null;
    }
}
