package com.acme.scaffold.common.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 开放平台客户端规则单测：redirect_uri 白名单结构与精确匹配。 */
class OpenApiClientRulesTest {

    @Test
    void shouldAcceptHttpsAndHttpLoopbackUris() {
        assertTrue(OpenApiClientRules.validateRedirectUris("https://admin.example.com/cb").isEmpty());
        assertTrue(OpenApiClientRules.validateRedirectUris("http://localhost:8080/cb").isEmpty());
        assertTrue(OpenApiClientRules.validateRedirectUris("http://LOCALHOST/cb").isEmpty());
        assertTrue(OpenApiClientRules.validateRedirectUris("http://127.0.0.1/cb").isEmpty());
        // 回归：IPv6 回环方括号内的冒号曾 early-return 为 '['，导致文档允许的 [::1] 永远非法。
        assertTrue(OpenApiClientRules.validateRedirectUris("http://[::1]").isEmpty());
        assertTrue(OpenApiClientRules.validateRedirectUris("http://[::1]:8080/cb").isEmpty());
    }

    @Test
    void shouldRejectNonLoopbackHttpAndMalformedAuthorities() {
        for (String uri : List.of(
                "http://evil.example.com/cb",
                "http://[::1]@evil.example.com/cb",
                "http://[::1]evil/cb",
                "http://[::1]:bad/cb",
                "http://localhost:70000/cb",
                "https://")) {
            assertFalse(OpenApiClientRules.validateRedirectUris(uri).isEmpty(), uri);
        }
    }

    @Test
    void errorShouldMentionIpv6LoopbackInAllowedForms() {
        List<String> errors = OpenApiClientRules.validateRedirectUris("http://evil.example.com/cb");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("localhost/127.0.0.1/[::1]"));
    }

    @Test
    void requestedRedirectUriMustMatchRegisteredExactly() {
        Set<String> registered = Set.of("https://admin.example.com/cb");
        assertTrue(OpenApiClientRules.isRedirectUriAllowed(registered, "https://admin.example.com/cb"));
        assertFalse(OpenApiClientRules.isRedirectUriAllowed(registered, "https://admin.example.com/cb/extra"));
        assertFalse(OpenApiClientRules.isRedirectUriAllowed(registered, null));
        // 存量未登记白名单保持向后兼容。
        assertTrue(OpenApiClientRules.isRedirectUriAllowed(Set.of(), "https://any.example.com/cb"));
    }
}
