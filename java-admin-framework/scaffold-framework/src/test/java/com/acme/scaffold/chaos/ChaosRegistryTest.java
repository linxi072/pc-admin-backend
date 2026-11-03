package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.model.ChaosFault;
import com.acme.scaffold.chaos.model.ChaosFaultType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChaosRegistry} 单元测试：arm / disarm / clear / 按 URI 的 Ant 风格路径匹配。
 */
@DisplayName("ChaosRegistry 单元测试")
class ChaosRegistryTest {

    private static ChaosFault fault(String id, String pattern) {
        return new ChaosFault(id, ChaosFaultType.LATENCY, pattern, 10, 503, 1.0, "t");
    }

    @Test
    @DisplayName("arm 后 list 可见；disarm 按 id 移除；clear 清空")
    void armDisarmClear() {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f1", "/api/pay/**"));
        registry.arm(fault("f2", "/api/users/*"));
        assertEquals(2, registry.list().size());

        registry.disarm("f1");
        assertEquals(1, registry.list().size());
        assertTrue(registry.list().stream().allMatch(f -> f.id().equals("f2")));

        registry.clear();
        assertTrue(registry.list().isEmpty());
    }

    @Test
    @DisplayName("arm 同 id 幂等覆盖")
    void armIdempotentOverride() {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f1", "/api/pay/**"));
        registry.arm(fault("f1", "/api/orders/**"));
        assertEquals(1, registry.list().size());
        assertEquals("/api/orders/**", registry.list().get(0).pathPattern());
    }

    @Test
    @DisplayName("match 按 Ant 风格路径命中（** 多级 / * 单级 / 不命中）")
    void matchByAntPattern() {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f1", "/api/pay/**"));
        registry.arm(fault("f2", "/api/users/*"));

        Optional<ChaosFault> hit1 = registry.match("/api/pay/order/1");
        assertTrue(hit1.isPresent());
        assertEquals("f1", hit1.get().id());

        Optional<ChaosFault> hit2 = registry.match("/api/users/42");
        assertTrue(hit2.isPresent());
        assertEquals("f2", hit2.get().id());

        // * 不匹配多级路径
        assertFalse(registry.match("/api/users/42/detail").isPresent());
        // 不命中任何规则
        assertFalse(registry.match("/api/health").isPresent());
        // null 安全
        assertFalse(registry.match(null).isPresent());
    }
}
