package com.acme.scaffold.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClientIpResolver} 单测：纯函数验证，无需 Spring 上下文。
 *
 * <p>重点覆盖「客户端伪造 X-Forwarded-For」的几种形态——该头左段永远不可信，
 * 解析必须自右向左跳过可信代理跳数，否则以 IP 为键的限流可被无限换键绕过。</p>
 */
class ClientIpResolverTest {

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader(ClientIpResolver.HEADER_X_FORWARDED_FOR, xff);
        }
        return req;
    }

    @Test
    @DisplayName("可信跳数 0：完全忽略 XFF，取直连地址（应用直连公网的正确取值）")
    void trustedProxyCountZeroIgnoresForwardedHeader() {
        MockHttpServletRequest req = request("203.0.113.7", "1.1.1.1");
        assertEquals("203.0.113.7", ClientIpResolver.resolve(req, 0));
    }

    @Test
    @DisplayName("单级代理：取 XFF 末段（= 客户端真实 IP）")
    void singleProxyTakesLastHop() {
        MockHttpServletRequest req = request("127.0.0.1", "203.0.113.7");
        assertEquals("203.0.113.7", ClientIpResolver.resolve(req, 1));
    }

    @Test
    @DisplayName("多级代理：按可信跳数自右向左定位，跳数不足时取更靠近服务端的一跳")
    void multiProxyChain() {
        MockHttpServletRequest req = request("10.0.0.9", "203.0.113.7, 10.0.0.1");
        // 2 级可信代理 → 客户端是最左段
        assertEquals("203.0.113.7", ClientIpResolver.resolve(req, 2));
        // 仅信任 1 级 → 客户端是 Nginx 看到的那一跳（第一级代理）
        assertEquals("10.0.0.1", ClientIpResolver.resolve(req, 1));
    }

    @Test
    @DisplayName("伪造防护：客户端自造长 XFF，跳数不足时回落直连地址而非采信左段")
    void forgedHeaderFallsBackToRemoteAddr() {
        // 实际只有 1 级代理，客户端却声明了 3 段；count=2 时链路长度不足，判定不可信
        MockHttpServletRequest req = request("10.0.0.9", "9.9.9.9, 8.8.8.8");
        assertEquals("10.0.0.9", ClientIpResolver.resolve(req, 3));
    }

    @Test
    @DisplayName("无 XFF 头：回落到直连地址")
    void missingHeaderFallsBackToRemoteAddr() {
        MockHttpServletRequest req = request("192.168.1.20", null);
        assertEquals("192.168.1.20", ClientIpResolver.resolve(req, 1));
        assertEquals("192.168.1.20", ClientIpResolver.resolve(req, 2));
    }

    @Test
    @DisplayName("空白 XFF 头（如 \" \" 或 \",,\"）：视为无头，回落直连地址")
    void blankHeaderFallsBackToRemoteAddr() {
        assertEquals("192.168.1.20", ClientIpResolver.resolve(request("192.168.1.20", "  "), 1));
        assertEquals("192.168.1.20", ClientIpResolver.resolve(request("192.168.1.20", " , , "), 1));
    }

    @Test
    @DisplayName("归一化：去空白、剥 IPv4 尾随端口、剥 IPv6 方括号，IPv6 原文保持不动")
    void normalizeHops() {
        assertEquals("203.0.113.7", ClientIpResolver.normalize("  203.0.113.7:5678  "));
        assertEquals("2001:db8::1", ClientIpResolver.normalize("2001:db8::1"));
        assertEquals("::1", ClientIpResolver.normalize("[::1]:8080"));
        assertEquals("", ClientIpResolver.normalize(null));
        // 拆分时丢弃空段，保持顺序
        assertEquals(List.of("1.1.1.1", "2.2.2.2"), ClientIpResolver.splitHops("1.1.1.1, ,2.2.2.2"));
        assertTrue(ClientIpResolver.splitHops(null).isEmpty());
    }

    @Test
    @DisplayName("非 HTTP 上下文（定时任务 / MQ 消费）：返回 unknown 而非 NPE")
    void nullRequestReturnsUnknown() {
        assertEquals(ClientIpResolver.UNKNOWN, ClientIpResolver.resolve(null, 1));
    }

    @Test
    @DisplayName("实例方法按构造入参解析；负数跳数按 0 钳制")
    void instanceUsesConfiguredCount() {
        ClientIpResolver resolver = new ClientIpResolver(1);
        assertEquals("203.0.113.7", resolver.resolve(request("127.0.0.1", "203.0.113.7")));

        ClientIpResolver clamped = new ClientIpResolver(-5);
        assertEquals("127.0.0.1", clamped.resolve(request("127.0.0.1", "9.9.9.9")));
    }
}
