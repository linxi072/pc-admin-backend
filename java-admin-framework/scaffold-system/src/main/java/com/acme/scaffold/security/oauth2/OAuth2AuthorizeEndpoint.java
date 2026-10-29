package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.openapi.OpenApiClientService;
import com.acme.scaffold.system.openapi.domain.OpenApiClientDO;
import com.acme.scaffold.system.openapi.rules.OpenApiClientRules;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OAuth2 授权端点（{@code GET /oauth2/authorize}，{@code response_type=code}）。
 *
 * <p><b>资源拥有者必须已认证</b>：本端点不加入 {@code SecurityConfig#BASE_PUBLIC_API}，
 * 由资源服务器过滤器链要求其携带合法 Bearer 令牌（即已登录用户）；未认证返回 401 / {@code login_required}。
 * 这与本平台「无状态 Bearer / 无 Cookie 会话」架构一致——登录态由调用方已有的访问令牌证明。</p>
 *
 * <p>校验 client_id 为启用中的客户端、请求 scope 为其授权范围的子集后，签发一次性短时效授权码
 * （见 {@link OAuth2AuthorizationCodeService}），返回 {@code {"code", "state"}}。
 * RFC 6749 中授权码应以 302 重定向回 {@code redirect_uri} 下发；本平台为 API 优先，直接以 JSON 返回，
 * 由调用方（通常是前端 SPA）将 code 交给令牌端点兑换，等效达成授权委托。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2AuthorizeEndpoint {

    private final OpenApiClientService clientService;
    private final OAuth2AuthorizationCodeService codeService;

    public OAuth2AuthorizeEndpoint(OpenApiClientService clientService,
                                   OAuth2AuthorizationCodeService codeService) {
        this.clientService = clientService;
        this.codeService = codeService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state) {

        if (!"code".equals(responseType)) {
            return error(HttpStatus.BAD_REQUEST, "unsupported_response_type", "仅支持 response_type=code");
        }

        OpenApiClientDO client;
        try {
            client = clientService.getActiveClient(clientId);
        } catch (BusinessException e) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "客户端无效或未启用");
        }

        Set<String> clientScopes = OpenApiClientRules.parseScopes(client.getScopes());
        Set<String> reqScopes = (scope == null || scope.isBlank())
                ? Set.of() : OpenApiClientRules.parseScopes(scope);
        if (!clientScopes.containsAll(reqScopes)) {
            return error(HttpStatus.BAD_REQUEST, "invalid_scope", "请求的 scope 超出客户端授权范围");
        }
        String grantedScope = reqScopes.isEmpty() ? client.getScopes() : scope;

        CurrentPrincipal principal = currentPrincipal();
        if (principal == null || principal.userId() == null) {
            return error(HttpStatus.UNAUTHORIZED, "login_required", "资源拥有者未认证");
        }

        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                principal.userId(), principal.username(), principal.tokenVersion(),
                principal.permissions(), principal.roles(), clientId, grantedScope, redirectUri);
        String code = codeService.issue(clientId, redirectUri, ctx);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        if (state != null) {
            body.put("state", state);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static CurrentPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentPrincipal p) {
            return p;
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", desc);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
