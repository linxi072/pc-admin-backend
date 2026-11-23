package com.acme.scaffold.config.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 网关灰度路由规则单测（E10）：权重边界、稳定分桶、显式 header/IP 规则优先。 */
class GatewayCanaryRulesTest {

    @Test
    void shouldClampWeightBoundary() {
        assertEquals(0, GatewayCanaryRules.clamp(-5));
        assertEquals(100, GatewayCanaryRules.clamp(200));
        assertEquals(50, GatewayCanaryRules.clamp(50));
        assertFalse(GatewayCanaryRules.route("u1", 0, null, null, null, null).isCanary());
        assertTrue(GatewayCanaryRules.route("u1", 100, null, null, null, null).isCanary());
        assertEquals("weight_0", GatewayCanaryRules.route("u1", 0, null, null, null, null).reason());
        assertEquals("weight_100", GatewayCanaryRules.route("u1", 100, null, null, null, null).reason());
    }

    @Test
    void shouldBucketStably() {
        int a = GatewayCanaryRules.bucket("user-123");
        assertEquals(a, GatewayCanaryRules.bucket("user-123"));
        assertEquals(a, GatewayCanaryRules.bucket("USER-123")); // 大小写不敏感
        assertTrue(a >= 0 && a < 100);
        assertEquals(0, GatewayCanaryRules.bucket(null));
        assertEquals(0, GatewayCanaryRules.bucket("  "));
    }

    @Test
    void shouldRouteByWeightHash() {
        int canaryCount = 0;
        for (int i = 0; i < 100; i++) {
            if (GatewayCanaryRules.route("user-" + i, 30, null, null, null, null).isCanary()) {
                canaryCount++;
            }
        }
        assertTrue(canaryCount > 20 && canaryCount < 40, "canaryCount=" + canaryCount);
        // 同一 key 在相同权重下结论恒定
        boolean first = GatewayCanaryRules.route("sticky", 30, null, null, null, null).isCanary();
        for (int i = 0; i < 10; i++) {
            assertEquals(first, GatewayCanaryRules.route("sticky", 30, null, null, null, null).isCanary());
        }
    }

    @Test
    void shouldForceCanaryByHeaderOrIp() {
        var byHeader = GatewayCanaryRules.route("u1", 0, "X-Canary", "true", null, null);
        assertTrue(byHeader.isCanary());
        assertTrue(byHeader.reason().startsWith("header:"));
        assertFalse(GatewayCanaryRules.route("u1", 0, "X-Canary", "false", null, null).isCanary());

        var byIp = GatewayCanaryRules.route("u1", 0, null, null, List.of(" 10.0.0.1 "), "10.0.0.1");
        assertTrue(byIp.isCanary());
        assertEquals("ip_whitelist", byIp.reason());
        assertFalse(GatewayCanaryRules.route("u1", 0, null, null, List.of("10.0.0.1"), "10.0.0.2").isCanary());
    }
}
