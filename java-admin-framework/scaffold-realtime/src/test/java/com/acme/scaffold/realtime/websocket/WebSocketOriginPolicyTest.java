package com.acme.scaffold.realtime.websocket;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketOriginPolicyTest {

    @Test
    void allowAllWhenStarPresent() {
        WebSocketOriginPolicy policy = new WebSocketOriginPolicy(List.of("*"));
        assertTrue(policy.allows("https://evil.example.com"));
        assertTrue(policy.allows(null));
        assertTrue(policy.allows(""));
    }

    @Test
    void nullOriginAllowedWhenRestricted() {
        // 非浏览器 / 同源脚本无 Origin 头：CSWSH 不适用，放行交由 JWT 校验兜底
        WebSocketOriginPolicy policy = new WebSocketOriginPolicy(List.of("https://admin.example.com"));
        assertTrue(policy.allows(null));
        assertTrue(policy.allows(""));
    }

    @Test
    void exactMatchAllowed() {
        WebSocketOriginPolicy policy = new WebSocketOriginPolicy(List.of("https://admin.example.com"));
        assertTrue(policy.allows("https://admin.example.com"));
        assertFalse(policy.allows("https://evil.example.com"));
    }

    @Test
    void wildcardSuffixAndPrefix() {
        WebSocketOriginPolicy policy = new WebSocketOriginPolicy(
                List.of("http://localhost:*", "*.example.com"));
        assertTrue(policy.allows("http://localhost:3000"));
        assertTrue(policy.allows("https://app.example.com"));
        // 前缀不匹配
        assertFalse(policy.allows("http://localhost.example.org"));
        assertFalse(policy.allows("https://other.com"));
    }

    @Test
    void emptyListFallsBackToAllowAll() {
        WebSocketOriginPolicy policy = new WebSocketOriginPolicy(List.of());
        assertTrue(policy.allows("https://anything.example.com"));
    }

    @Test
    void normalizedListExposed() {
        WebSocketOriginPolicy policy = new WebSocketOriginPolicy(List.of(" https://admin.example.com "));
        assertTrue(policy.allows("https://admin.example.com"));
    }
}
