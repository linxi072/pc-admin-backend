package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import java.time.LocalDateTime;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OAuth2 用户同意(consent)应用服务（#900）。
 *
 * <p>判定某次授权是否可「跳过同意页」（已存在覆盖所请求 scope 的已批准、未过期同意），
 * 以及在用户允许后将同意落库（支持「记住授权」复用）。落库经 {@link OAuth2ConsentDataPort} 防腐层，
 * 由 system 侧适配器实现，security 不直连持久化。</p>
 */
@Component
@RequiredArgsConstructor
public class OAuth2ConsentService {

    private final OAuth2ConsentDataPort consentPort;

    /** 是否已批准：存在未过期的 APPROVED 同意且其 scope 覆盖请求的 scope 集合。 */
    public boolean isApproved(Long userId, String clientId, Set<String> requestedScopes) {
        return consentPort.findLatestApproved(userId, clientId)
                .filter(info -> info.expiresAt() == null || info.expiresAt().isAfter(LocalDateTime.now()))
                .map(info -> OpenApiClientRules.parseScopes(info.scopes()))
                .map(approved -> approved.containsAll(requestedScopes))
                .orElse(false);
    }

    /** 记录同意（remember=false 时不落库，仅本次授权有效）。 */
    public void record(Long userId, String clientId, Set<String> scopes, boolean remember) {
        if (!remember) {
            return;
        }
        consentPort.recordApproved(userId, clientId,
                OpenApiClientRules.normalizeScopes(String.join(",", scopes)));
    }
}
