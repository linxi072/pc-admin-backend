package com.acme.scaffold.config.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.api.Result;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 统一运维控制台单测（E2）：开关注册表完整性 + 聚合接口对显式配置/默认值的读取。
 */
class OpsConsoleTest {

    @Test
    void registryShouldBeCompleteAndConsistent() {
        assertTrue(FeatureGateRegistry.size() >= 10);
        assertTrue(FeatureGateRegistry.contains("gateway.enabled"));
        assertTrue(FeatureGateRegistry.contains("app.chaos.enabled"));
        assertTrue(FeatureGateRegistry.contains("realtime.gateway.hmac.enabled"));
        assertNull(FeatureGateRegistry.find("nonexistent.enabled"));
        assertFalse(FeatureGateRegistry.contains(null));

        // 默认态与 @ConditionalOnProperty(matchIfMissing) 一致的关键项
        assertEquals(true, FeatureGateRegistry.find("app.quartz.enabled").defaultEnabled());
        assertEquals(true, FeatureGateRegistry.find("app.cache.enabled").defaultEnabled());
        assertEquals(false, FeatureGateRegistry.find("app.chaos.enabled").defaultEnabled());
        assertEquals(false, FeatureGateRegistry.find("gateway.enabled").defaultEnabled());
    }

    @Test
    void gatesShouldReflectEnvironmentOverridesAndDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("gateway.enabled", "true");
        env.setProperty("app.chaos.enabled", "false");
        OpsConsoleController controller = new OpsConsoleController(env);

        Result<OpsConsoleController.OpsOverview> result = controller.gates();
        assertNotNull(result);
        OpsConsoleController.OpsOverview overview = result.data();
        assertEquals(FeatureGateRegistry.size(), overview.totalCount());
        assertEquals(overview.gates().size(), overview.enabledCount() + countDisabled(overview));

        OpsConsoleController.GateStatus gateway = find(overview, "gateway.enabled");
        assertTrue(gateway.enabled(), "显式 true 覆盖默认 false");
        assertFalse(gateway.fromDefault());

        OpsConsoleController.GateStatus chaos = find(overview, "app.chaos.enabled");
        assertFalse(chaos.enabled(), "显式 false");
        assertFalse(chaos.fromDefault());

        OpsConsoleController.GateStatus quartz = find(overview, "app.quartz.enabled");
        assertTrue(quartz.enabled(), "未配置时取默认 true");
        assertTrue(quartz.fromDefault());
    }

    @Test
    void gatesWithEmptyEnvironmentShouldUseAllDefaults() {
        OpsConsoleController controller = new OpsConsoleController(new MockEnvironment());
        OpsConsoleController.OpsOverview overview = controller.gates().data();
        long enabledDefaults = FeatureGateRegistry.ALL.stream()
                .filter(FeatureGateRegistry.Gate::defaultEnabled).count();
        assertEquals((int) enabledDefaults, overview.enabledCount());
        assertTrue(overview.gates().stream().allMatch(OpsConsoleController.GateStatus::fromDefault));
    }

    private static int countDisabled(OpsConsoleController.OpsOverview o) {
        return (int) o.gates().stream().filter(g -> !g.enabled()).count();
    }

    private static OpsConsoleController.GateStatus find(OpsConsoleController.OpsOverview o, String key) {
        for (OpsConsoleController.GateStatus g : o.gates()) {
            if (g.property().equals(key)) return g;
        }
        throw new AssertionError("未找到开关: " + key);
    }
}
