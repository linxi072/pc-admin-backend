package com.acme.scaffold.config.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 网关 CORS 规则单测（方向 B）：覆盖预检识别、来源白名单与回显值（fail-closed）。
 */
class GatewayCorsRulesTest {

    @Test
    void shouldIdentifyPreflight() {
        assertTrue(GatewayCorsRules.isPreflight("OPTIONS", "GET"));
        assertTrue(GatewayCorsRules.isPreflight(" options ", "POST"));
        assertFalse(GatewayCorsRules.isPreflight("OPTIONS", null));
        assertFalse(GatewayCorsRules.isPreflight("OPTIONS", "  "));
        assertFalse(GatewayCorsRules.isPreflight("GET", "GET"));
    }

    @Test
    void shouldMatchAllowedOriginsCaseInsensitively() {
        List<String> allowed = List.of("https://admin.example.com");
        assertTrue(GatewayCorsRules.isAllowedOrigin("https://admin.example.com", allowed));
        assertTrue(GatewayCorsRules.isAllowedOrigin(" HTTPS://ADMIN.EXAMPLE.COM ", allowed));
        assertFalse(GatewayCorsRules.isAllowedOrigin("https://evil.com", allowed));
        assertFalse(GatewayCorsRules.isAllowedOrigin(null, allowed));
        assertFalse(GatewayCorsRules.isAllowedOrigin("https://admin.example.com", List.of()));
    }

    @Test
    void wildcardShouldEchoAsterisk() {
        List<String> allowed = List.of("*");
        assertTrue(GatewayCorsRules.isAllowedOrigin("https://any.com", allowed));
        assertEquals("*", GatewayCorsRules.allowOriginValue("https://any.com", allowed));
    }

    @Test
    void disallowedOriginShouldNotGetAllowHeader() {
        assertNull(GatewayCorsRules.allowOriginValue("https://evil.com", List.of("https://admin.example.com")));
        assertEquals("https://admin.example.com",
                GatewayCorsRules.allowOriginValue("https://admin.example.com", List.of("https://admin.example.com")));
    }
}
