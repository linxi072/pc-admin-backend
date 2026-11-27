package com.acme.scaffold.security.antireplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 防重放过滤器回归：缓存饱和返回 429，仍有效的 nonce 重放返回 409。 */
class AntiReplayFilterCacheFullTest {

    @Test
    void cacheFullShouldReturnTooManyRequestsAndNotInvokeChain() throws Exception {
        AntiReplayFilter filter = filter(1);
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("nonce-1"), first, noopChain());
        assertEquals(200, first.getStatus());

        MockHttpServletResponse second = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        filter.doFilter(request("nonce-2"), second, chainInvoked(invoked));

        assertEquals(429, second.getStatus());
        assertTrue(second.getContentAsString().contains("AUTH_014"));
        assertFalse(invoked.get());
    }

    @Test
    void replayShouldStillReturnConflictWhenCacheIsFull() throws Exception {
        AntiReplayFilter filter = filter(1);
        filter.doFilter(request("nonce-1"), new MockHttpServletResponse(), noopChain());

        MockHttpServletResponse replay = new MockHttpServletResponse();
        filter.doFilter(request("nonce-1"), replay, noopChain());

        assertEquals(409, replay.getStatus());
        assertTrue(replay.getContentAsString().contains("AUTH_013"));
    }

    private static AntiReplayFilter filter(int maxSize) {
        AntiReplayProperties props = new AntiReplayProperties();
        props.setEnabled(true);
        props.setNonceTtlSeconds(60);
        props.setMaxNonceCacheSize(maxSize);
        return new AntiReplayFilter(props, new ReplayNonceService(props));
    }

    private static MockHttpServletRequest request(String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.addHeader("X-Request-Timestamp", String.valueOf(System.currentTimeMillis()));
        request.addHeader("X-Request-Nonce", nonce);
        return request;
    }

    private static FilterChain noopChain() {
        return (req, res) -> { };
    }

    private static FilterChain chainInvoked(AtomicBoolean flag) {
        return (req, res) -> flag.set(true);
    }
}
