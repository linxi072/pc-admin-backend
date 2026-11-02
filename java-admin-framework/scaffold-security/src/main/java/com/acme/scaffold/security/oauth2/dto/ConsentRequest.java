package com.acme.scaffold.security.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用户同意(consent)决策请求体（{@code POST /oauth2/consent}，#900）。
 *
 * <p>由授权端点经 {@code consent_required} 引导至此：前端展示同意页后，携带签名
 * {@code consent_token} 回传 allow/deny 决策。JSON 字段采用 snake_case，与 OAuth2 协议参数风格一致，
 * 故显式声明 {@link JsonProperty} 绑定（Jackson 默认 camelCase 不会自动识别下划线字段）。</p>
 *
 * @param consentToken 授权端点签发的 RS256 consent_token（含 client_id/scope/redirect_uri/state/PKCE 挑战）
 * @param decision     allow（允许）或 deny（拒绝）
 * @param remember     是否「记住授权」（下次同客户端同 scope 跳过同意页）；缺省视为 true
 */
public record ConsentRequest(
        @JsonProperty("consent_token") String consentToken,
        @JsonProperty("decision") String decision,
        @JsonProperty("remember") Boolean remember) {
}
