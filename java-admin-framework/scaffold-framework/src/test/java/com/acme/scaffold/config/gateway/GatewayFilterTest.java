package com.acme.scaffold.config.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 网关前置过滤器集成单测（方向 B，无真实容器）：内网准入 / CORS 预检终结 / 全局限流短路。
 */
class GatewayFilterTest {

    private static final class NoopChain implements FilterChain {
        private final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            called.set(true);
        }
    }

    private static GatewayProperties props() {
        GatewayProperties p = new GatewayProperties();
        p.setEnabled(true);
        return p;
    }

    @Test
    void actuatorFromPublicIpShouldBeForbidden() throws ServletException, IOException {
        GatewayProperties p = props();
        GatewayFilter filter = new GatewayFilter(p, new ClientIpResolver(0));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse res = new MockHttpServletResponse();
        NoopChain chain = new NoopChain();

        filter.doFilter(req, res, chain);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("GATEWAY_ACTUATOR_FORBIDDEN"));
        assertTrue(!chain.called.get(), "应短路，不转发业务");
    }

    @Test
    void actuatorFromInternalIpShouldPass() throws ServletException, IOException {
        GatewayFilter filter = new GatewayFilter(props(), new ClientIpResolver(0));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        NoopChain chain = new NoopChain();

        filter.doFilter(req, res, chain);
        assertTrue(chain.called.get(), "内网 IP 应放行到业务");
    }

    @Test
    void corsPreflightShouldBeTerminatedByGateway() throws ServletException, IOException {
        GatewayProperties p = props();
        p.setAllowedOrigins(java.util.List.of("https://admin.example.com"));
        GatewayFilter filter = new GatewayFilter(p, new ClientIpResolver(0));
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/system/user");
        req.addHeader("Origin", "https://admin.example.com");
        req.addHeader("Access-Control-Request-Method", "GET");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        NoopChain chain = new NoopChain();

        filter.doFilter(req, res, chain);
        assertEquals(204, res.getStatus());
        assertEquals("https://admin.example.com", res.getHeader("Access-Control-Allow-Origin"));
        assertTrue(!chain.called.get(), "预检应被网关终结，不转发业务");
    }

    @Test
    void rateLimitShouldShortcutAfterMax() throws ServletException, IOException {
        GatewayProperties p = props();
        p.setRateLimitMax(2);
        p.setRateLimitWindowSeconds(60);
        GatewayFilter filter = new GatewayFilter(p, new ClientIpResolver(0));
        NoopChain chain = new NoopChain();

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/a");
            req.setRemoteAddr("1.2.3.4");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }
        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/api/a");
        req3.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, chain);

        assertEquals(429, res3.getStatus());
        assertTrue(res3.getContentAsString().contains("GATEWAY_RATE_LIMITED"));
    }
}
