package com.acme.scaffold.config.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.config.gateway.GatewayRouteRules.RouteType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 网关路由分类规则单测（方向 B）：覆盖四类路由、URI 规范化、限流判定与 actuator 内网准入。
 */
class GatewayRouteRulesTest {

    @Test
    void shouldClassifyAllRouteTypes() {
        assertEquals(RouteType.API, GatewayRouteRules.classify("/api/system/user/list"));
        assertEquals(RouteType.API, GatewayRouteRules.classify("/api"));
        assertEquals(RouteType.WEBSOCKET, GatewayRouteRules.classify("/ws/notify"));
        assertEquals(RouteType.WEBSOCKET, GatewayRouteRules.classify("/websocket/notify"));
        assertEquals(RouteType.ACTUATOR, GatewayRouteRules.classify("/actuator/health"));
        assertEquals(RouteType.ACTUATOR, GatewayRouteRules.classify("/livez"));
        assertEquals(RouteType.ACTUATOR, GatewayRouteRules.classify("/readyz"));
        assertEquals(RouteType.OTHER, GatewayRouteRules.classify("/doc.html"));
        assertEquals(RouteType.OTHER, GatewayRouteRules.classify("/"));
    }

    @Test
    void normalizeShouldStripQueryAndCollapseSlashes() {
        assertEquals("/api/system/user", GatewayRouteRules.normalize("/API//system/user?a=1#f"));
        assertEquals("", GatewayRouteRules.normalize(null));
        assertEquals("", GatewayRouteRules.normalize(""));
        assertEquals("/api", GatewayRouteRules.normalize("api"));
    }

    @Test
    void classificationShouldIgnoreQueryAndCase() {
        assertEquals(RouteType.API, GatewayRouteRules.classify("/API/system/user?x=1"));
        assertEquals(RouteType.ACTUATOR, GatewayRouteRules.classify("/Actuator/health"));
    }

    @Test
    void rateLimitShouldOnlyApplyToApiAndWebsocket() {
        assertTrue(GatewayRouteRules.shouldRateLimit("/api/a"));
        assertTrue(GatewayRouteRules.shouldRateLimit("/ws/a"));
        assertFalse(GatewayRouteRules.shouldRateLimit("/actuator/health"));
        assertFalse(GatewayRouteRules.shouldRateLimit("/doc.html"));
    }

    @Test
    void actuatorShouldRequireInternalIp() {
        List<String> internal = List.of("127.", "10.", "192.168.");
        assertTrue(GatewayRouteRules.allowActuator("/api/a", "8.8.8.8", internal), "非 actuator 不受限");
        assertTrue(GatewayRouteRules.allowActuator("/actuator/health", "10.0.0.5", internal));
        assertFalse(GatewayRouteRules.allowActuator("/actuator/health", "8.8.8.8", internal));
        assertFalse(GatewayRouteRules.allowActuator("/actuator/health", null, internal));
        assertFalse(GatewayRouteRules.allowActuator("/actuator/health", "10.0.0.5", List.of()),
                "内网白名单为空时 fail-closed");
        assertFalse(GatewayRouteRules.allowActuator("/actuator/health", "10.0.0.5", null));
    }
}
