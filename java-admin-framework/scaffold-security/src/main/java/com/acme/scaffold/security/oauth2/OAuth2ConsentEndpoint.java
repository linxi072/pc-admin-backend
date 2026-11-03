package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import com.acme.scaffold.security.oauth2.dto.ConsentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OAuth2 用户同意(consent)端点（{@code POST /oauth2/consent}，#900）。
 *
 * <p>由授权端点经 {@code consent_required} 引导至此：前端展示同意页后，携带签名
 * {@code consent_token} 回传 allow/deny 决策。允许后记录同意（支持「记住授权」），并签发一次性授权码
 * （code 绑定 consent_token 中的 PKCE 挑战、scope 与 redirect_uri），由调用方继续走令牌端点兑换。</p>
 *
 * <p>端点要求已认证（Bearer）；且 consent_token 的 subject 须与当前登录用户一致，杜绝跨用户授权。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2ConsentEndpoint {

    private final OAuth2AuthorizationCodeService codeService;
    private final OAuth2ConsentService consentService;
    private final NimbusJwtDecoder rs256Decoder;

    public OAuth2ConsentEndpoint(OAuth2AuthorizationCodeService codeService,
                                 OAuth2ConsentService consentService,
                                 RsaKeyProvider keys) {
        this.codeService = codeService;
        this.consentService = consentService;
        this.rs256Decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
    }

    @PostMapping(value = "/consent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> consent(@RequestBody ConsentRequest body) {
        Jwt jwt;
        try {
            jwt = rs256Decoder.decode(body.consentToken());
        } catch (JwtException e) {
            return error(HttpStatus.BAD_REQUEST, "invalid_grant", "consent_token 无效或已过期");
        }

        CurrentPrincipal principal = currentPrincipal();
        if (principal == null || principal.userId() == null) {
            return error(HttpStatus.UNAUTHORIZED, "login_required", "资源拥有者未认证");
        }
        if (!principal.userId().toString().equals(jwt.getSubject())) {
            return error(HttpStatus.BAD_REQUEST, "invalid_grant", "consent_token 与当前用户不匹配");
        }

        if (!"allow".equals(body.decision())) {
            return error(HttpStatus.BAD_REQUEST, "access_denied", "用户拒绝授权");
        }

        String clientId = jwt.getClaimAsString("client_id");
        String scope = jwt.getClaimAsString("scope");
        String redirectUri = jwt.getClaimAsString("redirect_uri");
        if (redirectUri == null) {
            redirectUri = "";
        }
        String state = jwt.getClaimAsString("state");
        if (state == null) {
            state = "";
        }
        String codeChallenge = jwt.getClaimAsString("code_challenge");
        String codeChallengeMethod = jwt.getClaimAsString("code_challenge_method");

        boolean remember = body.remember() != null ? body.remember() : true;
        Set<String> scopes = OpenApiClientRules.parseScopes(scope);
        consentService.record(principal.userId(), clientId, scopes, remember);

        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                principal.userId(), principal.username(), principal.tokenVersion(),
                principal.permissions(), principal.roles(), clientId, scope, redirectUri,
                codeChallenge, codeChallengeMethod);
        String code = codeService.issue(clientId, redirectUri, ctx);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", code);
        if (!state.isEmpty()) {
            resp.put("state", state);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
    }

    private static CurrentPrincipal currentPrincipal() {
        return OAuth2EndpointSupport.currentPrincipal();
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        return OAuth2EndpointSupport.error(status, code, desc);
    }
}
