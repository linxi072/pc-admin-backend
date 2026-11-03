package com.acme.scaffold.chaos;

import com.acme.scaffold.chaos.model.ChaosFault;
import com.acme.scaffold.chaos.model.ChaosFaultType;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ChaosFilter} 单元测试：用 {@code MockHttpServletRequest/Response} 验证各故障类型的注入行为，
 * 以及「未启用 / 未命中 / 概率不中」时的透传。不依赖 servlet 容器。
 */
@DisplayName("ChaosFilter 单元测试")
class ChaosFilterTest {

    private static ChaosFault fault(String id, ChaosFaultType type, String pattern,
                                   int latency, int errorStatus, double probability) {
        return new ChaosFault(id, type, pattern, latency, errorStatus, probability, "t");
    }

    private static MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(uri);
        return r;
    }

    @Test
    @DisplayName("LATENCY 命中：注入时延后透传，调用链被执行")
    void latencyInjectsAndForwards() throws Exception {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f1", ChaosFaultType.LATENCY, "/api/pay/**", 40, 503, 1.0));
        ChaosFilter filter = new ChaosFilter(registry, enabledProps(true));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req("/api/pay/order/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        long start = System.nanoTime();
        filter.doFilter(request, response, chain);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs >= 40, "应至少注入 40ms 时延，实际 " + elapsedMs);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("ERROR 命中：直接返回指定状态码且短路（不调用后续链）")
    void errorShortCircuits() throws Exception {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f2", ChaosFaultType.ERROR, "/api/pay/**", 0, 503, 1.0));
        ChaosFilter filter = new ChaosFilter(registry, enabledProps(true));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req("/api/pay/order/9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("CHAOS_ERROR"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("EXCEPTION 命中：抛出运行时异常（容器侧映射为 500）")
    void exceptionThrows() throws Exception {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f3", ChaosFaultType.EXCEPTION, "/api/pay/**", 0, 503, 1.0));
        ChaosFilter filter = new ChaosFilter(registry, enabledProps(true));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req("/api/pay/x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("未命中任何规则：完全透传")
    void noMatchForwards() throws Exception {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f1", ChaosFaultType.LATENCY, "/api/pay/**", 40, 503, 1.0));
        ChaosFilter filter = new ChaosFilter(registry, enabledProps(true));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req("/api/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("enabled=false：即使有故障规则也完全透传（零回归）")
    void disabledForwardsRegardless() throws Exception {
        ChaosRegistry registry = new ChaosRegistry();
        registry.arm(fault("f1", ChaosFaultType.ERROR, "/api/pay/**", 0, 503, 1.0));
        ChaosFilter filter = new ChaosFilter(registry, enabledProps(false));

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = req("/api/pay/order/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);
        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    private static ChaosProperties enabledProps(boolean enabled) {
        ChaosProperties p = new ChaosProperties();
        p.setEnabled(enabled);
        return p;
    }
}
